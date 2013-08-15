package com.solt.media.stream;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Map.Entry;
import java.util.BitSet;
import java.util.TreeMap;

import com.solt.libtorrent.LibTorrent;
import com.solt.libtorrent.PartialPieceInfo;
import com.solt.libtorrent.PiecesState;
import com.solt.libtorrent.TorrentException;
import com.solt.media.stream.helper.HttpDataHelper;
import com.solt.media.stream.helper.MdDataHelper;
import com.solt.media.stream.helper.TDataHelper;
import com.solt.media.stream.helper.TDataHelper.Result;
import com.solt.media.util.Average;

public class HyperStreamer implements TorrentStreamer {
	private static int DEFAULT_BUFFER_SECS = 60;
	private static int DEFAULT_MIN_PIECES_TO_BUFFER = 5;
	private HttpHandler handler;
	private String hashCode;
	private long pending;
	private long fileOffset;
	private LibTorrent libTorrent;
	private SocketChannel schannel;
	private int pieceSize;
	private TDataHelper helper;
	private int streamPiece;
	private int endPiece;
	private Average streamRate;
	private int startPiece;
	private int startPieceOffset;
	private byte[] buff;
	private BitSet helpedPieces;

	public HyperStreamer(HttpHandler handler, long movieId, String hashCode, int index,
			long dataLength, long fileOffset) throws TorrentException,
			IOException {
		this.handler = handler;
		this.hashCode = hashCode;
		this.pending = dataLength;
		this.fileOffset = fileOffset;
		this.libTorrent = handler.getHttpd().getLibTorrent();
		long torrentOffset = fileOffset + libTorrent.getTorrentFiles(hashCode)[index]
				.getOffset();
		pieceSize = libTorrent.getPieceSize(hashCode, false);
		buff = new byte[pieceSize];
		streamPiece = (int) (torrentOffset / pieceSize);
		endPiece = (int) ((torrentOffset + pending) / pieceSize) + 1;
		streamRate = Average.getInstance(1000, 20);
		startPiece = streamPiece;
		startPieceOffset = (int) (torrentOffset - startPiece
				* pieceSize);
		this.schannel = handler.getSocketChannel();
		schannel.configureBlocking(false);
//		helper = new HttpDataHelper(libTorrent, hashCode, index, fileOffset, dataLength);
		helper = new MdDataHelper(libTorrent, hashCode, movieId, index);
		helpedPieces = new BitSet(libTorrent.getPieceNum(hashCode));
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
		int setRead = -1;
		long speed = libTorrent.getTorrentDownloadRate(hashCode, true);
		if (speed < 300 * 1024 && libTorrent.getFirstPieceIncomplete(hashCode, streamPiece) == streamPiece) {
			tryUseDataHelperV1();
		}
		if (isSeek(fileOffset, pending) || (isRequestMetadata(pending) && libTorrent.getFirstPieceIncomplete(hashCode, streamPiece) == streamPiece)) {
			// TODO clear piece deadline
			libTorrent.clearPiecesDeadline(hashCode);
		}

		int lastDLP = streamPiece;
		int incompleteIdx = streamPiece;
		int numSet = 0;
		
		ByteBuffer readBuffer = ByteBuffer.allocate(1024);
		TreeMap<Integer, Long> dlPieces = new TreeMap<Integer, Long>();
		int currCancelPiece = -1;
		int state = 0;
		long bonusTime = 10000;
		long timeToWait = 0;
		long cancelTime = 0;
		boolean wait = false;
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
			
//			System.err.println("PIECE_BUFFER_SIZE = " + PIECE_BUFFER_SIZE);
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
				
				speed = libTorrent.getTorrentDownloadRate(hashCode, true);
				if (currCancelPiece != incompleteIdx) {
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
					System.err.println("wait " + streamPiece);
					Thread.sleep(500);
					checkEOF(schannel, readBuffer);
					wait = true;
					//use TDataHelper for fast stream
					needDataHelp(pState, speed);
					if (streamPiece == libTorrent.getFirstPieceIncomplete(hashCode, streamPiece)) {
						tryUseDataHelperV1();
					}
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

	private boolean needDataHelp(PiecesState state, long speed) throws TorrentException {
		if (speed > 300 * 1024) {
			return false;
		}
		if (state.getNumDone() / (float) state.getLenght() < 0.3f) {
			int last = state.getLastIncomplete();
			if (helpedPieces.get(last)) {
				last = state.getLastIncomplete(helpedPieces.previousClearBit(last));
			}
			if (last > streamPiece && helper.getPieceRemain(last, buff)) {
				System.err.println("needDataHelp: " + last);
				libTorrent.addTorrentPiece(hashCode, last, buff);
				helpedPieces.set(last);
			}
		}
		long remainBytes = getPieceRemain(streamPiece);
		return (remainBytes / (float)pieceSize > 0.7f) && (speed < 150 * 1024);
	}

	private void tryUseDataHelper() throws TorrentException, IOException, InterruptedException {
		if (helpedPieces.get(streamPiece)) {
			return;
		}
		Result result = helper.retrievePiece(streamPiece, buff);
		if (result.getState() != Result.ERROR) {
			System.err.println("helper: " + streamPiece);
			if (result.getState() == Result.COMPLETE) {
				libTorrent.addTorrentPiece(hashCode, streamPiece, buff);
				helpedPieces.set(streamPiece);
			}
			int offset = result.getOffset();
			int len = result.getLength();
			if (len > pending) {
				len = (int) pending;
			}
			writeData(schannel, buff, offset, len, streamRate);
			pending -= len;
			++streamPiece;
		}
	}
	
	private void tryUseDataHelperV1() throws TorrentException, IOException, InterruptedException {
		if (helpedPieces.get(streamPiece)) {
			return;
		}
		if (helper.getPieceRemain(streamPiece, buff)) {
			System.err.println("helper V1: " + streamPiece);
			libTorrent.addTorrentPiece(hashCode, streamPiece, buff);
			helpedPieces.set(streamPiece);
		} else {
			tryUseDataHelper();
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

	@Override
	public void close() {
		helper.close();
	}
}
