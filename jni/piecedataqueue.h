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

namespace solt {
using namespace libtorrent;
class piece_data_queue {
private:
	std::map<int, read_piece_alert*> queue;
	mutable boost::mutex mutex;
public:
	piece_data_queue();

	read_piece_alert* push(int p, read_piece_alert* alrt);

	read_piece_alert* pop(int p);

	void clear();

	~piece_data_queue();
};

} /* namespace solt */
#endif /* PIECEDATAQUEUE_H_ */
