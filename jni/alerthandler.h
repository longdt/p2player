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
		torrent_deleted, torrent_add, save_resume_data, others
	};

	torrent_alert_handler(sha1_hash torrent_hash, alert_type expected_type) :
		done(false), error_alert(false), torrent_hash(torrent_hash), expected_type(expected_type) {
	}

	torrent_alert_handler() : done(false), error_alert(false), expected_type(others){}

	bool handle(const alert* a);

	inline alert_type get_expected_type() {return expected_type;}
	inline bool is_done(){return done;}
	inline bool is_error_alert() {return error_alert;}
	inline sha1_hash info_hash() {return torrent_hash;}

	~torrent_alert_handler() {}
private:
	alert_type expected_type;
	bool error_alert;
	bool done;
	sha1_hash torrent_hash;
};

inline torrent_alert_handler::alert_type get_alert_type(const libtorrent::alert* a) {
	return torrent_alert_handler::alert_type::others;
}
} /* namespace solt */
#endif /* ALERTHANDLER_H_ */
