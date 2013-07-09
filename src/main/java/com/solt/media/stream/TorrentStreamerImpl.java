package com.solt.media.stream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.solt.libtorrent.LibTorrent;
import com.solt.libtorrent.PartialPieceInfo;
import com.solt.libtorrent.PiecesState;
import com.solt.libtorrent.TorrentException;
import com.solt.media.util.Average;

public class TorrentStreamerImpl implements TorrentStreamer {
	private static final StreamManager manager = new StreamManager();
	private static int DEFAULT_BUFFER_SECS = 60;
	private static int DEFAULT_MIN_PIECES_TO_BUFFER = 5;
	private HttpHandler handler;
	private String hashCode;
	private int index;
	private long pending;
	private long fileOffset;
	private LibTorrent libTorrent;
	private SocketChannel schannel;
	private int pieceSize;

	public TorrentStreamerImpl(HttpHandler handler, String hashCode, int index,
			long dataLength, long fileOffset) throws TorrentException,
			IOException {
		this.handler = handler;
		this.hashCode = hashCode;
		this.index = index;
		this.pending = dataLength;
		this.fileOffset = fileOffset;
		this.libTorrent = handler.getHttpd().getLibTorrent();
		pieceSize = libTorrent.getPieceSize(hashCode, false);
		this.schannel = handler.getSocketChannel();
		schannel.configureBlocking(false);
		manager.putSync(hashCode, this);
	}
	
	public static boolean isSeek(long transOffset, long dataLength) {
		float seekPoint = transOffset / (float) (transOffset + dataLength);
		return transOffset > (8 * 1024 * 1024) || seekPoint > 0.1 && !isRequestMetadata(dataLength);
	}
	
	public static boolean isRequestMetadata(long dataLength) {
		return dataLength > 30000 && dataLength < 50000;
	}

	/* (non-Javadoc)
	 * @see com.solt.media.stream.TorrentStreamer#stream()
	 */
	@Override
	public void stream() throws Exception {
		long torrentOffset = fileOffset + libTorrent.getTorrentFiles(hashCode)[index]
				.getOffset();
		int streamPiece = (int) (torrentOffset / pieceSize);
		int endPiece = (int) ((torrentOffset + pending) / pieceSize) + 1;
		int setRead = -1;
		int startPiece = streamPiece;
		int startPieceOffset = (int) (torrentOffset - startPiece
				* pieceSize);
		if (isSeek(fileOffset, pending) || (isRequestMetadata(pending) && libTorrent.getFirstPieceIncomplete(hashCode, streamPiece) == streamPiece)) {
			// TODO clear piece deadline
			libTorrent.clearPiecesDeadline(hashCode);
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
		Average streamRate = Average.getInstance(1000, 20);
		long timeToWait = 0;
		long cancelTime = 0;
		boolean wait = false;
		long speed = 0;
		PiecesState pState = new PiecesState(hashCode);
		while (handler.isStreaming() && pending > 0
				&& !Thread.currentThread().isInterrupted()) {
			if (state != 4 && state != 5 && state != 3) {
				state = libTorrent.getTorrentState(hashCode);
			}
			int PIECE_BUFFER_SIZE = computePieceBufferSize(hashCode, pieceSize,
					streamRate, wait);
			incompleteIdx = libTorrent.getFirstPieceIncomplete(hashCode,
					streamPiece);
			
			System.err.println("PIECE_BUFFER_SIZE = " + PIECE_BUFFER_SIZE);
			if (state != 4 && state != 5
					&& streamPiece + PIECE_BUFFER_SIZE > incompleteIdx) {
				//set deadline
				long currentTime = System.currentTimeMillis();
				System.err.println(streamPiece);
				if (incompleteIdx > lastDLP) {
					lastDLP = incompleteIdx;
				}
				pState.setFromIdx(incompleteIdx);
				pState.setLength(PIECE_BUFFER_SIZE, false);
				libTorrent.getPieceState(pState);
				numSet = incompleteIdx + PIECE_BUFFER_SIZE - lastDLP + pState.getNumDone();
				if (numSet > 0) {
					// System.err.println("set deadline: [" + lastSet + ", "
					// + (lastSet + numSet) + ")");
					dlPieces.put(lastDLP, currentTime);
					lastDLP = setDeadline(lastDLP, numSet, endPiece);
					//setPriority(lastDLP + 1, 15);
				}
				
				//cancel slow piece
				Entry<Integer, Long> entry = dlPieces
						.floorEntry(incompleteIdx);
				if (entry == null) {
					System.err.println("ERROR set deadline");
				}
//				if (speed < 50000 && currCancelPiece != incompleteIdx) {
//					speed = libTorrent.getTorrentDownloadRate(hashCode, true);
//					bonusTime = pieceSize * 1000l/ (speed + 1024);
//					timeToWait = bonusTime * 3;
//					if (bonusTime < 3000) {
//						bonusTime = 3000;
//					}
//				}
				
				if (currCancelPiece != incompleteIdx) {
					speed = libTorrent.getTorrentDownloadRate(hashCode, true);
					bonusTime = pieceSize * 1000l/ (speed + 1024);
					timeToWait = bonusTime * 3 + 3000;
					if (bonusTime < 3000) {
						bonusTime = 3000;
					}
					if (entry.getValue() + timeToWait + (incompleteIdx - entry.getKey()) * bonusTime < currentTime) {
						currCancelPiece = incompleteIdx;
						libTorrent.cancelTorrentPiece(hashCode, currCancelPiece);
						cancelTime = currentTime;
						timeToWait = bonusTime + 1000; //wait bonus time
					}
				} else {
					speed = libTorrent.getTorrentDownloadRate(hashCode, true);
					bonusTime = getPieceRemain(currCancelPiece) * 1000l / (speed + 1024);
					if (speed > 500000 && bonusTime < 1000) {
						bonusTime += 1000;
					}
					timeToWait = bonusTime * 3 + 3000; //wait bonus time
					if (cancelTime + timeToWait < currentTime) {
						libTorrent.cancelTorrentPiece(hashCode, currCancelPiece, true);
						cancelTime = currentTime;
					}
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
					wait = true;
					continue;
				}
			}
			wait = false;
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
			int offset = (streamPiece == startPiece) ? startPieceOffset
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

	protected long getPieceRemain(int currCancelPiece) throws TorrentException {
		PartialPieceInfo info = libTorrent.getPartialPieceInfo(hashCode, currCancelPiece);
		if (info == null) {
			return pieceSize;
		}
		return (pieceSize - info.getDownloadedBytes());
	}

	protected int setDeadline(int fromIndex, int count, int endIndex) throws TorrentException {
		if (count == 0) {
			return fromIndex;
		}
		int n = fromIndex + count;
		if (n > endIndex) {
			n = endIndex;
		}
		int i = 0;
		for (; fromIndex < n; ++fromIndex, ++i) {
			libTorrent.setPieceDeadline(hashCode, fromIndex, i * 150 + 1500);
		}
		return (fromIndex - 1);
	}
	
	protected int setPriority(int index, int count, int endIndex) throws TorrentException {
		if (count == 0) {
			return index;
		}
		int n = index + count;
		if (n > endIndex) {
			n = endIndex;
		}
		for (; index < n; ++index) {
			libTorrent.setPiecePriority(hashCode, index, 7);
		}
		return (index - 1);
	}

	private int computePieceBufferSize(String hashCode, int pieceSize,
			Average streamRate, boolean wait) {

		long rate = streamRate.getAverage();
		try {
			long downRate = libTorrent.getTorrentDownloadRate(hashCode, true);
			if (!wait && rate < downRate) {
				rate = (long) (rate + rate * 0.2);
			} else if (wait) {
				rate = (long) (downRate * 1.2);
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
