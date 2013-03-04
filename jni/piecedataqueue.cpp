/*
 * piecedataqueue.cpp
 *
 *  Created on: May 15, 2012
 *      Author: user
 */

#include "piecedataqueue.h"
#include "jniutils.h"
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

bool piece_data_queue::set_read(int p) {
#ifdef STD_C++_11
	std::unordered_map<int, int>::iterator it;
#else
	std::map<int, int>::iterator it;
#endif
	boost::mutex::scoped_lock l(mutex);
	if ((it = piece_flag.find(p)) != piece_flag.end()) {
		it->second += 1;
		return false;
	} else {
		piece_flag[p] = 1;
		return true;
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

read_piece_alert* piece_data_queue::pop(int p, bool &is_remove) {
#ifdef STD_C++_11
	std::unordered_map<int, int>::iterator it;
#else
	std::map<int, int>::iterator it;
#endif
	is_remove = false;
	read_piece_alert* result = NULL;
	boost::mutex::scoped_lock l(mutex);
	std::map<int, read_piece_alert*>::iterator iter =
			queue.find(p);
	if (iter != queue.end()) {
		result = iter->second;
		it = piece_flag.find(p);
		if (it == piece_flag.end()) {
			LOG_ERR("error missing set read piece");
		} else if (it->second == 1) {
			piece_flag.erase(it);
			queue.erase(iter);
			is_remove = true;
		} else {
			it->second = it->second - 1;
		}
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
