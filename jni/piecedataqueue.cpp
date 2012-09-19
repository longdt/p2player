/*
 * piecedataqueue.cpp
 *
 *  Created on: May 15, 2012
 *      Author: user
 */

#include "piecedataqueue.h"

namespace solt {

piece_data_queue::piece_data_queue() {
	// TODO Auto-generated constructor stub

}

piece_data_queue::~piece_data_queue() {
	boost::mutex::scoped_lock l(mutex);
	for (std::map<int, read_piece_alert*>::iterator it = queue.begin(), end = queue.end(); it != end; ++it) {
		delete it->second;
	}
}

read_piece_alert* piece_data_queue::push(int p, read_piece_alert* alrt) {
	read_piece_alert* old = NULL;
	boost::mutex::scoped_lock l(mutex);
	std::map<int, read_piece_alert*>::iterator iter =
			queue.find(p);
	if (iter != queue.end()) {
		old = iter->second;
		iter->second = alrt;
	} else {
		queue[p] = alrt;
	}
	return old;
}

read_piece_alert* piece_data_queue::pop(int p) {
	read_piece_alert* result = NULL;
	boost::mutex::scoped_lock l(mutex);
	std::map<int, read_piece_alert*>::iterator iter =
			queue.find(p);
	if (iter != queue.end()) {
		result = iter->second;
		queue.erase(iter);
	}
	return result;
}

void piece_data_queue::clear() {
	boost::mutex::scoped_lock l(mutex);
	for (std::map<int, read_piece_alert*>::iterator it = queue.begin(), end = queue.end(); it != end; ++it) {
		delete it->second;
	}
	queue.clear();
}

} /* namespace solt */
