/*
 * stream.cpp
 *
 *  Created on: May 8, 2012
 *      Author: user
 */
#include "stream.hpp"
#include "concurrentqueue.h"
#include "libtorrent/piece_picker.hpp"
#include "libtorrent/torrent.hpp"
#include "libtorrent/extensions.hpp"
#include "com_solt_libtorrent_LibTorrent.h"
#include "jniutils.h"
#include "libtorrent/peer_connection.hpp"
#include "torrentinfo.h"

namespace solt {


struct stream_plugin : torrent_plugin {
	stream_plugin(torrent& t, concurrent_queue<cancel_piece> *userdata) :
			m_torrent(t), cancel_piece_task(userdata), last_time(time_now()), piece_index(
					0) {
	}

	void tick() {
		cancel_piece piece;
		while (cancel_piece_task->pop(&piece)) {
			LOG_ERR("cancel piece[%d]", piece.index);
			if (m_torrent.has_picker() && !m_torrent.is_finished()) {
				cancel_request(piece.index, piece.force);
			}
		}
	}

private:
	void cancel_request(int piece_idx, bool force) {
		piece_picker& picker = m_torrent.picker();
		const std::vector<piece_picker::downloading_piece> &m_downloads =
				picker.get_download_queue();
		std::vector<piece_picker::downloading_piece>::const_iterator iter =
				std::find_if(m_downloads.begin(), m_downloads.end(),
						piece_picker::has_index(piece_idx));
		if (iter == m_downloads.end() || iter->writing + iter->finished == 0)
			return;

		int num_blocks_in_piece = picker.blocks_in_piece(piece_idx);
		int counter = iter->requested;
		policy::peer* p = NULL;
		for (int i = 0; i < num_blocks_in_piece; ++i) {
			const piece_picker::block_info& info = iter->info[i];
			if (info.state == piece_picker::block_info::state_requested) {
				p = static_cast<policy::peer*>(info.peer);
				if (p->connection && (force || !is_downloading(piece_idx, i, p->connection))) {
#ifdef SOLT_TORRENT_ABORT_PB_BY_PIECE_PICKER
					picker.abort_download(piece_block(piece_idx, i));
#else
					p->connection->cancel_request(piece_block(piece_idx, i), true);
#endif
				}
				--counter;
				if (counter == 0) break;
			}
		}
	}
	/*
	 * Only call this method on blocks in request state.
	 */
	bool is_downloading(int piece_idx, int block_idx, const peer_connection* connection) {
		boost::optional<piece_block_progress> pbp
			= connection->downloading_piece_progress();
		if (pbp && pbp->piece_index == piece_idx && pbp->block_index == block_idx && pbp->bytes_downloaded > 0) {
			return true;
		}
		return false;
	}

	torrent& m_torrent;
	concurrent_queue<cancel_piece> *cancel_piece_task;
	ptime last_time;
	int piece_index;
};

boost::shared_ptr<torrent_plugin> create_stream_plugin(torrent* t,
		void* userdata) {
	// don't add this extension if the torrent is private
	if (t->valid_metadata() && t->torrent_file().priv())
		return boost::shared_ptr<torrent_plugin>();
	return boost::shared_ptr<torrent_plugin>(
			new stream_plugin(*t, (concurrent_queue<cancel_piece>*) userdata));
}

}

