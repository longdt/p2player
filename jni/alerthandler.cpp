/*
 * alerthandler.cpp
 *
 *  Created on: May 15, 2012
 *      Author: user
 */

#include "alerthandler.h"
#include "jniutils.h"
#include "libtorrent/bencode.hpp"
#include <map>
#include "torrentinfo.h"

namespace solt {

void torrent_alert_handler::operator()(torrent_finished_alert const& a) {
	if (a.handle.is_valid()) {
		a.handle.save_resume_data();
		if (expected_type == alert_type::torrent_finished && torrent_hash == a.handle.info_hash()) {
			--num_alert;
		}
	}
	error_alert = false;
	last_type = alert_type::torrent_finished;
}

void torrent_alert_handler::operator()(save_resume_data_alert const& a) {
	if (a.handle.is_valid() && a.resume_data) {
		std::vector<char> out;
		libtorrent::bencode(std::back_inserter(out),
				*(a.resume_data));
		boost::filesystem::path savePath = a.handle.save_path();
		savePath /= (a.handle.name() + RESUME_SUFFIX);
		solt::SaveFile(savePath, out);

		if (expected_type == alert_type::save_resume_data && torrent_hash == a.handle.info_hash()) {
			--num_alert;
		}
	}
	error_alert = false;
	last_type = alert_type::save_resume_data;
}

void torrent_alert_handler::operator()(save_resume_data_failed_alert const& a) {
	if (a.handle.is_valid()) {
		if (expected_type == alert_type::save_resume_data && torrent_hash == a.handle.info_hash()) {
			--num_alert;
		}
	}
	error_alert = true;
	last_type = alert_type::save_resume_data;
}

void torrent_alert_handler::operator()(torrent_deleted_alert const& a) {
	if (expected_type == alert_type::torrent_deleted && torrent_hash == a.info_hash) {
		--num_alert;
	}
	error_alert = false;
	last_type = alert_type::torrent_deleted;
}

void torrent_alert_handler::operator()(torrent_delete_failed_alert const& a) {
	if (a.handle.is_valid()) {
		if (expected_type == alert_type::torrent_deleted && torrent_hash == a.handle.info_hash()) {
			--num_alert;
		}
	}
	error_alert = true;
	last_type = alert_type::torrent_deleted;
}

void hand_read_piece_alert(const read_piece_alert *a) {
	if (a->handle.is_valid()) {
		TorrentInfo* pTorrentInfo = GetTorrentInfo(a->handle.info_hash());
		if (pTorrentInfo) {
			read_piece_alert* old = (pTorrentInfo->piece_queue).push(a->piece,(read_piece_alert *) a->clone().release());
			if (old) {
				delete old;
			}
		}
	}
}

bool handle_read_piece_alert(sha1_hash &torrent_hash, piece_data_queue &queue, int expected_piece, const libtorrent::alert* a) {
	const libtorrent::read_piece_alert* readPieceAlrt = libtorrent::alert_cast<
						libtorrent::read_piece_alert>(a);
	if (readPieceAlrt) {
		if(readPieceAlrt->handle.info_hash() == torrent_hash) {
			if (readPieceAlrt->piece == expected_piece) {
				return true;
			} else {
				read_piece_alert* old = queue.push(readPieceAlrt->piece, (read_piece_alert *)readPieceAlrt->clone().release());
				if (old) {
					delete old;
				}
			}
		} else {
			hand_read_piece_alert(readPieceAlrt);
		}
		return false;
	}
	const libtorrent::torrent_finished_alert* finAlrt = libtorrent::alert_cast<
				libtorrent::torrent_finished_alert>(a);
	if (finAlrt) {
		finAlrt->handle.save_resume_data();
		return false;
	}
	const libtorrent::save_resume_data_alert* saveAlrt =
			libtorrent::alert_cast<libtorrent::save_resume_data_alert>(a);
	if (saveAlrt && saveAlrt->handle.is_valid() && saveAlrt->resume_data) {
		std::vector<char> out;
		libtorrent::bencode(std::back_inserter(out),
				*(saveAlrt->resume_data));
		boost::filesystem::path savePath = saveAlrt->handle.save_path();
		savePath /= (saveAlrt->handle.name() + RESUME_SUFFIX);
		solt::SaveFile(savePath, out);
		return false;
	}
	return false;
}

void handle_alert(torrent_alert_handler &handler,const libtorrent::alert* a) {
	const libtorrent::torrent_finished_alert* finAlrt = libtorrent::alert_cast<
			libtorrent::torrent_finished_alert>(a);
	if (finAlrt) {
		LOG_ERR("torrent_finished_alert");
		handler(*finAlrt);
		return;
	}
	const libtorrent::save_resume_data_alert* saveAlrt =
			libtorrent::alert_cast<libtorrent::save_resume_data_alert>(a);
	if (saveAlrt) {
		LOG_ERR("save_resume_data_alert");
		handler(*saveAlrt);
		return;
	}
	const libtorrent::save_resume_data_failed_alert* saveFailAlrt =
				libtorrent::alert_cast<libtorrent::save_resume_data_failed_alert>(a);
	if (saveFailAlrt) {
		LOG_ERR("save_resume_data_failed_alert");
		handler(*saveFailAlrt);
		return;
	}
	const libtorrent::torrent_deleted_alert* delAlrt =
			libtorrent::alert_cast<libtorrent::torrent_deleted_alert>(a);
	if (delAlrt) {
		LOG_ERR("torrent_deleted_alert");
		handler(*delAlrt);
		return;
	}
	const libtorrent::torrent_delete_failed_alert* delFailAlrt = libtorrent::alert_cast<
			libtorrent::torrent_delete_failed_alert>(a);
	if (delFailAlrt) {
		LOG_ERR("torrent_delete_failed_alert");
		handler(*delFailAlrt);
		return;
	}

	const libtorrent::read_piece_alert* readPieceAlrt = libtorrent::alert_cast<
				libtorrent::read_piece_alert>(a);
	if (readPieceAlrt) {
		LOG_ERR("read_piece_alert");
		hand_read_piece_alert(readPieceAlrt);
		return;
	}
}

void handle_remove_torrent_alert(torrent_alert_handler &handler,const libtorrent::alert* a) {
	const libtorrent::read_piece_alert* readPieceAlrt = libtorrent::alert_cast<
					libtorrent::read_piece_alert>(a);
	if (readPieceAlrt && readPieceAlrt->handle.info_hash() == handler.info_hash()) {
		return;
	}
	handle_alert(handler, a);
}

} /* namespace solt */
