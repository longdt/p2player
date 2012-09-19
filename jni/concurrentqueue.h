/*
 * concurrentqueue.h
 *
 *  Created on: May 9, 2012
 *      Author: user
 */

#ifndef CONCURRENTQUEUE_H_
#define CONCURRENTQUEUE_H_
#include <queue>
#include <boost/thread.hpp>
namespace solt {

template<class T> class concurrent_queue {
private:
	std::queue<T> backend;
	mutable boost::mutex mutex;
public:
	bool empty() const {
		boost::mutex::scoped_lock l(mutex);
		return backend.empty();
	}
	T& back() {
		boost::mutex::scoped_lock l(mutex);
		return backend.back();
	}
	const T& back() const {
		boost::mutex::scoped_lock l(mutex);
		return backend.back();
	}

	T& front() {
		boost::mutex::scoped_lock l(mutex);
		return backend.front();
	}
	const T& front() const {
		boost::mutex::scoped_lock l(mutex);
		return backend.front();
	}

	bool pop(T* removedcopy) {
		boost::mutex::scoped_lock l(mutex);
		if (backend.empty()) {
			return false;
		} else if (removedcopy) {
			*removedcopy = backend.front();
		}
		backend.pop();
		return true;
	}

	void push(const T& item) {
		boost::mutex::scoped_lock l(mutex);
		backend.push(item);
	}

	int size() const {
		boost::mutex::scoped_lock l(mutex);
		return backend.size();
	}
};


} /* namespace solt */
#endif /* CONCURRENTQUEUE_H_ */
