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

namespace solt {


struct stream_plugin : torrent_plugin {
	stream_plugin(torrent& t, concurrent_queue<int> *userdata) :
			m_torrent(t), cancel_piece_task(userdata), last_time(time_now()), piece_index(
					0) {
	}

	void tick() {
		int cancel_piece = -1;
		while (cancel_piece_task->pop(&cancel_piece)) {
			LOG_ERR("cancel piece[%d]", cancel_piece);
			if (m_torrent.has_picker() && !m_torrent.is_finished()) {
				cancel_request(cancel_piece);
			}
		}
	}

private:
	void cancel_request(int piece_idx) {
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
		for (int i = 0; i < num_blocks_in_piece; ++i) {
			const piece_picker::block_info& info = iter->info[i];
			if (info.state == piece_picker::block_info::state_requested) {
				picker.abort_download(piece_block(piece_idx, i));
				--counter;
				if (counter == 0) break;
			}
		}
	}

	torrent& m_torrent;
	concurrent_queue<int> *cancel_piece_task;
	ptime last_time;
	int piece_index;
};

boost::shared_ptr<torrent_plugin> create_stream_plugin(torrent* t,
		void* userdata) {
	// don't add this extension if the torrent is private
	if (t->valid_metadata() && t->torrent_file().priv())
		return boost::shared_ptr<torrent_plugin>();
	return boost::shared_ptr<torrent_plugin>(
			new stream_plugin(*t, (concurrent_queue<int>*) userdata));
}

}

