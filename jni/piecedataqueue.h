/*
 * piecedataqueue.h
 *
 *  Created on: May 15, 2012
 *      Author: user
 */

#ifndef PIECEDATAQUEUE_H_
#define PIECEDATAQUEUE_H_
#include <boost/thread/mutex.hpp>
#include "libtorrent/alert_types.hpp"
#ifdef SOLT_TORRENT_STD_CPLUSPLUS_11
#include <unordered_map>
#endif
namespace solt {
using namespace libtorrent;
class piece_data_queue {
private:
	std::map<int, read_piece_alert*> queue;
#ifdef SOLT_TORRENT_STD_CPLUSPLUS_11
	std::unordered_map<int, int> piece_flag;
#else
	std::map<int, int> piece_flag;
#endif
	mutable boost::mutex mutex;
public:
	piece_data_queue();

	bool set_read(int p);

	read_piece_alert* push(int p, read_piece_alert* alrt);

	read_piece_alert* pop(int p, bool &is_remove);

	void clear();

	~piece_data_queue();
};

} /* namespace solt */
#endif /* PIECEDATAQUEUE_H_ */
