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

extern std::string gDefaultSave;
using namespace libtorrent;

namespace solt {

bool torrent_alert_handler::handle(const alert* a) {
#ifdef TORRENT_USE_OPENSSL
	if (const torrent_need_cert_alert* p = alert_cast<torrent_need_cert_alert>(a))
	{
		torrent_handle h = p->handle;
		error_code ec;
		file_status st;
		std::string cert = combine_path("certificates", to_hex(h.info_hash().to_string())) + ".pem";
		std::string priv = combine_path("certificates", to_hex(h.info_hash().to_string())) + "_key.pem";
		stat_file(cert, &st, ec);
		if (ec)
		{
			char msg[256];
			snprintf(msg, sizeof(msg), "ERROR. could not load certificate %s: %s\n", cert.c_str(), ec.message().c_str());
			if (g_log_file) fprintf(g_log_file, "[%s] %s\n", time_now_string(), msg);
			return true;
		}
		stat_file(priv, &st, ec);
		if (ec)
		{
			char msg[256];
			snprintf(msg, sizeof(msg), "ERROR. could not load private key %s: %s\n", priv.c_str(), ec.message().c_str());
			if (g_log_file) fprintf(g_log_file, "[%s] %s\n", time_now_string(), msg);
			return true;
		}

		char msg[256];
		snprintf(msg, sizeof(msg), "loaded certificate %s and key %s\n", cert.c_str(), priv.c_str());
		if (g_log_file) fprintf(g_log_file, "[%s] %s\n", time_now_string(), msg);

		h.set_ssl_certificate(cert, priv, "certificates/dhparams.pem", "1234");
		h.resume();
	}
#endif

	if (const add_torrent_alert* p = alert_cast<add_torrent_alert>(a))
	{
		if (expected_type == alert_type::torrent_add && torrent_hash == p->handle.info_hash()) {
			done = true;
		}
		if (p->error)
		{
			fprintf(stderr, "failed to add torrent: %s\n", p->error.message().c_str());
			error_alert = true;
		}
		else
		{
			error_alert = false;
			//update gTorrents
			TorrentInfo* pTorrentInfo = GetTorrentInfo(p->handle.info_hash());
			if (pTorrentInfo) {
				pTorrentInfo->handle = p->handle;
#ifdef START_ON_ADD
				if (p->handle.is_paused()) {
					p->handle.resume();
				}
#endif
			}
		}
	}
	else if (const torrent_finished_alert* p = alert_cast<torrent_finished_alert>(a))
	{
		p->handle.set_max_connections(50 / 2);

		// write resume data for the finished torrent
		// the alert handler for save_resume_data_alert
		// will save it to disk
		 p->handle.save_resume_data();
	}
	else if (const save_resume_data_alert* p = alert_cast<save_resume_data_alert>(a))
	{
		TORRENT_ASSERT(p->resume_data);
		if (p->resume_data)
		{
			std::vector<char> out;
			bencode(std::back_inserter(out), *p->resume_data);
			std::string filename = combine_path(gDefaultSave, combine_path(RESUME
											, to_hex(p->handle.info_hash().to_string()) + RESUME));
						solt::SaveFile(filename, out);
		}
		if (expected_type == alert_type::save_resume_data && torrent_hash == p->handle.info_hash()) {
			error_alert = false;
			done = true;
		}

	}
	else if (const save_resume_data_failed_alert* p = alert_cast<save_resume_data_failed_alert>(a))
	{
		if (expected_type == alert_type::save_resume_data && torrent_hash == p->handle.info_hash()) {
			error_alert = true;
			done = true;
		}
	}
	else if (const torrent_paused_alert* p = alert_cast<torrent_paused_alert>(a))
	{
		// write resume data for the finished torrent
		// the alert handler for save_resume_data_alert
		// will save it to disk
		p->handle.save_resume_data();
	} else if (const read_piece_alert* p = alert_cast<read_piece_alert>(a)) {
		if (p->handle.is_valid()) {
			TorrentInfo* pTorrentInfo = GetTorrentInfo(p->handle.info_hash());
			if (pTorrentInfo) {
				read_piece_alert* old = (pTorrentInfo->piece_queue).push(p->piece,(read_piece_alert *) p->clone().release());
				if (old) {
					delete old;
				}
			}
		}
	} else if (const torrent_deleted_alert* p = alert_cast<libtorrent::torrent_deleted_alert>(a)) {
		LOG_ERR("torrent_deleted_alert");
		if (expected_type == alert_type::torrent_deleted && torrent_hash == p->info_hash) {
			done = true;
			error_alert = false;
		}
	} else if (const torrent_delete_failed_alert* p = alert_cast<torrent_delete_failed_alert>(a)) {
		if (expected_type == alert_type::torrent_deleted && torrent_hash == p->handle.info_hash()) {
			done = true;
			error_alert = true;
		}
	}
	return done;
}

} /* namespace solt */
