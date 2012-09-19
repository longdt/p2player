/*
 * alerthandler.h
 *
 *  Created on: May 15, 2012
 *      Author: user
 */

#ifndef ALERTHANDLER_H_
#define ALERTHANDLER_H_
#include "libtorrent/alert_types.hpp"
#include "libtorrent/alert.hpp"
#include "piecedataqueue.h"

namespace solt {
using namespace libtorrent;

struct torrent_alert_handler {
public:
	enum alert_type {
		torrent_deleted, torrent_finished, save_resume_data, others
	};

	torrent_alert_handler(sha1_hash torrent_hash, alert_type expected_type,
			int num_alert) :
			torrent_hash(torrent_hash), expected_type(expected_type), num_alert(
					num_alert) {
	}
	void operator()(torrent_finished_alert const& a);
	void operator()(save_resume_data_alert const& a);
	void operator()(save_resume_data_failed_alert const& a);
	void operator()(torrent_deleted_alert const& a);
	void operator()(torrent_delete_failed_alert const& a);

	inline int get_num_alert() {return num_alert;}
	inline alert_type get_expected_type() {return expected_type;}
	inline alert_type get_alert_type() {return last_type;}
	inline bool is_error_alert() {return error_alert;}
	inline sha1_hash info_hash() {return torrent_hash;}

	~torrent_alert_handler() {}
private:
	int num_alert;
	alert_type expected_type;
	alert_type last_type;
	bool error_alert;
	sha1_hash torrent_hash;
};

bool handle_read_piece_alert(sha1_hash &torrent_hash, piece_data_queue &queue, int expected_piece, const libtorrent::alert* a);
void handle_remove_torrent_alert(torrent_alert_handler &handler,const libtorrent::alert* a);
} /* namespace solt */
#endif /* ALERTHANDLER_H_ */
