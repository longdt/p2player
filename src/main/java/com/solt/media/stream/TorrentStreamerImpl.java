package com.solt.media.stream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.solt.libtorrent.LibTorrent;
import com.solt.libtorrent.TorrentException;
import com.solt.media.util.Average;

public class TorrentStreamerImpl implements TorrentStreamer {
	private static int DEFAULT_BUFFER_SECS = 60;
	private static int DEFAULT_MIN_PIECES_TO_BUFFER = 5;
	private HttpHandler handler;
	private String hashCode;
	private int index;
	private long pending;
	private long transferOffset;
	private LibTorrent libTorrent;
	private SocketChannel schannel;
	private int pieceSize;
	private int pieceNum;

	public TorrentStreamerImpl(HttpHandler handler, String hashCode, int index,
			long dataLength, long transferOffset) throws TorrentException,
			IOException {
		this.handler = handler;
		this.hashCode = hashCode;
		this.index = index;
		this.pending = dataLength;
		this.transferOffset = transferOffset;
		this.libTorrent = handler.getHttpd().getLibTorrent();
		this.schannel = handler.getSocketChannel();
		pieceSize = libTorrent.getPieceSize(hashCode, false);
		pieceNum = libTorrent.getPieceNum(hashCode);
		schannel.configureBlocking(false);
	}

	/* (non-Javadoc)
	 * @see com.solt.media.stream.TorrentStreamer#stream()
	 */
	@Override
	public void stream() throws Exception {
		Average streamRate = Average.getInstance(1000, 20);
		// serveFile()
		// TODO transfer data when downloading torrent

		// int PIECE_BUFFER_SIZE = 300 * 1024 * 30 / pieceSize;
		long timeToWait = (pieceSize * 10000l) / (300 * 1024);
		transferOffset += libTorrent.getTorrentFiles(hashCode)[index]
				.getOffset();
		int streamPiece = (int) (transferOffset / pieceSize);
		int setRead = -1;
		int transferPieceIdx = streamPiece;
		int transferPieceOffset = (int) (transferOffset - transferPieceIdx
				* pieceSize);
		if (transferOffset > 0) {
			// TODO clear piece deadline
			// libTorrent.clearPiecesDeadline(hashCode);
		}

		int lastDLP = streamPiece;
		int incompleteIdx = streamPiece;
		int numSet = 0;
		byte[] buff = new byte[pieceSize];
		ByteBuffer readBuffer = ByteBuffer.allocate(1024);
		TreeMap<Integer, Long> dlPieces = new TreeMap<Integer, Long>();
		int currCancelPiece = -1;
		int state = 0;
		long bonusTime = 10000;
		boolean isWait = false;
		while (handler.isStreaming() && pending > 0
				&& !Thread.currentThread().isInterrupted()) {
			if (state != 4 && state != 5 && state != 3) {
				state = libTorrent.getTorrentState(hashCode);
			}
			int PIECE_BUFFER_SIZE = computePieceBufferSize(hashCode, pieceSize,
					streamRate, isWait);
			incompleteIdx = libTorrent.getFirstPieceIncomplete(hashCode,
					transferOffset);
			
			System.err.println("PIECE_BUFFER_SIZE = " + PIECE_BUFFER_SIZE);
			if (state != 4 && state != 5
					&& streamPiece + PIECE_BUFFER_SIZE > incompleteIdx) {
				//set deadline
				long currentTime = System.currentTimeMillis();
				System.err.println(streamPiece);
				if (incompleteIdx > lastDLP) {
					lastDLP = incompleteIdx;
				}
				numSet = incompleteIdx + PIECE_BUFFER_SIZE - lastDLP;
				if (numSet > 0) {
					// System.err.println("set deadline: [" + lastSet + ", "
					// + (lastSet + numSet) + ")");
					dlPieces.put(lastDLP, currentTime);
					lastDLP = setDeadline(lastDLP, numSet);
					setPriority(lastDLP + 1, 15);
				}
				//cancel slow piece
				if (currCancelPiece != incompleteIdx) {
					bonusTime = pieceSize * 1000l/ (libTorrent.getTorrentDownloadRate(hashCode, true) + 1024);
					timeToWait = bonusTime * 10;
					if (bonusTime < 3000) {
						bonusTime = 3000;
					}
				}
				Entry<Integer, Long> entry = dlPieces
						.floorEntry(incompleteIdx);
				if (entry == null) {
					System.err.println("ERROR set deadline");
				} else if (entry.getValue() + timeToWait + (incompleteIdx - entry.getKey()) * bonusTime < currentTime) {
					currCancelPiece = incompleteIdx;
					libTorrent.cancelTorrentPiece(hashCode, currCancelPiece);
					timeToWait = currentTime + bonusTime; //wait bonus time
				}
				//stop tracking downloaded deadline piece
				if (dlPieces.firstKey() < entry.getKey()) {
					dlPieces.remove(dlPieces.firstKey());
				}
				
				//wait for download piece
				if (streamPiece == incompleteIdx) {
					System.err.println("wait for libtorrent download data...");
					Thread.sleep(500);
					checkEOF(schannel, readBuffer);
					isWait = true;
					continue;
				}
			}
			isWait = false;
			//read piece data
			if (setRead != streamPiece) {
				setRead = streamPiece;
				libTorrent.setTorrentReadPiece(hashCode, setRead);
				Thread.sleep(50);
			}
			int len = libTorrent.readTorrentPiece(hashCode, streamPiece, buff);
			System.err.println("Read Piece: " + streamPiece + " with len: "
					+ len);
			if (len == -1) {
				break;
			} else if (len == 0) {
				Thread.sleep(50);
				checkEOF(schannel, readBuffer);
				continue;
			}
			int offset = (streamPiece == transferPieceIdx) ? transferPieceOffset
					: 0;
			len = len - offset;
			if (len > pending) {
				len = (int) pending;
			}
			writeData(schannel, buff, offset, len, streamRate);
			pending -= len;
			++streamPiece;
		}

	}

	private int setDeadline(int fromIndex, int count) throws TorrentException {
		if (count == 0) {
			return fromIndex;
		}
		int n = fromIndex + count;
		if (n > pieceNum) {
			n = pieceNum;
		}
		int i = 0;
		for (; fromIndex < n; ++fromIndex, ++i) {
			libTorrent.setPieceDeadline(hashCode, fromIndex, i * 150 + 1500);
		}
		return (fromIndex - 1);
	}
	
	private int setPriority(int index, int count) throws TorrentException {
		if (count == 0) {
			return index;
		}
		int n = index + count;
		if (n > pieceNum) {
			n = pieceNum;
		}
		for (; index < n; ++index) {
			libTorrent.setPiecePriority(hashCode, index, 7);
		}
		return (index - 1);
	}

	private int computePieceBufferSize(String hashCode, int pieceSize,
			Average streamRate, boolean isWait) {

		long rate = streamRate.getAverage();
		try {
			long downRate = libTorrent.getTorrentDownloadRate(hashCode, true);
			if (!isWait && rate < downRate) {
				rate = (long) (rate + rate * 0.2);
			} else if (isWait) {
				rate = (long) (downRate * 0.2);
			}
		} catch (TorrentException e) {
			e.printStackTrace();
		}

		int buffer_secs = DEFAULT_BUFFER_SECS;

		long buffer_bytes = (buffer_secs * rate);

		int pieces_to_buffer = (int) (buffer_bytes / pieceSize);

		if (pieces_to_buffer < DEFAULT_MIN_PIECES_TO_BUFFER) {

			pieces_to_buffer = DEFAULT_MIN_PIECES_TO_BUFFER;
		}
		return pieces_to_buffer;
	}

	private void checkEOF(SocketChannel sc, ByteBuffer readBuffer)
			throws IOException {
		int len = sc.read(readBuffer);
		if (len == -1) {
			throw new IOException("player send EOF signal");
		} else if (len > 0) {
			System.err.println("Player send data to server");
		}
	}

	private void writeData(SocketChannel sc, byte[] buff, int offset, int len,
			Average streamRate) throws IOException, InterruptedException {
		ByteBuffer buffer = ByteBuffer.wrap(buff, offset, len); // TODO Need
																// tunning
		sc.write(buffer);
		int writeLen = 0;
		while (buffer.hasRemaining()) {
			Thread.sleep(50);
			writeLen = sc.write(buffer);
			streamRate.addValue(writeLen);
		}
	}
}
