package com.solt.libtorrent;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Comparator;

import com.solt.media.stream.NanoHTTPD;

public class Test {
	private static volatile boolean shutdown = false;
	/**
	 * @param args
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws TorrentException 
	 */
	public static void main(String[] args) throws InterruptedException,
			IOException, TorrentException {
		System.out.println(Charset.defaultCharset());
		final LibTorrent libTorrent = new LibTorrent();
		
		libTorrent.setSession(18080, "./", 0, 0 * 1024);
		libTorrent.setSessionOptions(true, true, true, true);
		final NanoHTTPD httpd = new NanoHTTPD(18008, new File("./"), libTorrent);
		String torrentFile = "a.torrent";
		if (args.length > 0 && !args[0].trim().isEmpty()) {
			torrentFile = args[0].trim();
		}
		final String hashCode = libTorrent.addTorrent(torrentFile, 0, false);
		String mediaUrl = "http://localhost:18008/"
				+ URLEncoder.encode(hashCode, "UTF-8");
		System.out.println(mediaUrl);
		FileEntry[] files = libTorrent.getTorrentFiles(hashCode);
		for (FileEntry entry : files) {
			System.out.println("filepath: " + entry.getPath() + "\toffset: " + entry.getOffset() 
					+ "\tfilebase: " + entry.getFileBase()
					+ "\tsize: " + entry.getSize()
					+ "\tpadFile: " +entry.isPadFile());
			
		}
		
		int state = libTorrent.getTorrentState(hashCode);
		while (state == 7 || state == 1) {
			System.out.println("state:" + state);
			Thread.sleep(1000);
			state = libTorrent.getTorrentState(hashCode);
		}

		System.out.println("state:" + state);
		int gap = 3;
		int lastSet = 0;
		int numPiece = libTorrent.getPieceNum(hashCode);
		for (int i = 0; i < gap; ++i) {
			libTorrent.setPieceDeadline(hashCode, lastSet + i, i * 2 + 100);
			libTorrent.setPieceDeadline(hashCode, numPiece - i - 1,  i * 2 + 100);
		}

		Thread shutdowner = new Thread("shutdowner") {
			@Override
			public void run() {
				try {
					int c = 0;
					while ((c = System.in.read()) != 'q') {
						if (c == 's') {
							System.out.println(libTorrent.getSessionStatusText());
							System.out.println(libTorrent.getTorrentStatusText(hashCode));
						} else if (c == 'c') {
							httpd.cancelStream();
						} else if (c == 'p') {
							httpd.stop();
						} else if (c == 'r') {
							httpd.start();
						}
					}
					shutdown = true;
				} catch (IOException e) {
					e.printStackTrace();
				} catch (TorrentException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		};
		shutdowner.setDaemon(true);
		shutdowner.start();
		PieceInfoComparator comparator = new PieceInfoComparator();
		while (!shutdown) {
			PartialPieceInfo[] infos = libTorrent.getPieceDownloadQueue(hashCode);
			if (infos != null) {
				Arrays.sort(infos, comparator);
				for (int i = 0; i < infos.length; ++i) {
					System.out.println("piece[" + infos[i].getPieceIdx() + "]: "
						+ infos[i].getPieceState() + progressPiece(infos[i]));
				}
				System.out.println();
			}
			Thread.sleep(1000);
		}
		System.out.println(libTorrent.getTorrentStatusText(hashCode));
		System.out.println("start remove torrent");
		httpd.shutdown();
		libTorrent.removeTorrent(hashCode, false);
		System.out.println("removed");
		libTorrent.pauseSession();
		libTorrent.abortSession();
	}

	private static String progressPiece(PartialPieceInfo info) {
		StringBuilder builder = new StringBuilder();
		int[] blocks = info.getBlocks();
		int totalBytes = 0;
		for (int i = 0; i < info.getNumBlocks(); ++i) {
			totalBytes += blocks[i * 4 + 1];
			int state = blocks[i * 4];
			if (state == 3) {
				builder.append('#');
			} else if (state == 2) {
				builder.append('=');
			} else if (state == 1) {
				builder.append('+');
			} else if (state == 0) {
				builder.append('_');
			} else {
				builder.append(' ');
			}
		}
		builder.append('\t').append(totalBytes);
		return builder.toString();
	}

}

class PieceInfoComparator implements Comparator<PartialPieceInfo> {

	@Override
	public int compare(PartialPieceInfo o1, PartialPieceInfo o2) {
		if (o1.getPieceIdx() < o2.getPieceIdx()) {
			return -1;
		} else if (o1.getPieceIdx() > o2.getPieceIdx()) {
			return 1;
		} else {
			return 0;
		}
	}
	
}