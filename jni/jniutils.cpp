/*
 * jniutils.cpp
 *
 *  Created on: May 17, 2012
 *      Author: user
 */
#include "jniutils.h"
extern solt::jniobject gJniObject;

namespace solt {
int SaveFile(std::string const& filename, std::vector<char>& v) {
	libtorrent::file f;
	libtorrent::error_code ec;
	if (!f.open(filename, libtorrent::file::write_only, ec)) {
		LOG_ERR("cant open to write file %s due: %s", filename.c_str(), ec.message().c_str());
		return -1;
	}
	if (ec) {
		LOG_ERR("cant open to write file %s due: %s", filename.c_str(), ec.message().c_str());
		return -1;
	}
	libtorrent::file::iovec_t b = { &v[0], v.size() };
	libtorrent::size_type written = f.writev(0, &b, 1, ec);
	if (written != v.size()) {
		LOG_ERR("error when write file %s", filename.c_str());
		return -3;
	}
	if (ec) {
		LOG_ERR("error when write file %s due: %s", filename.c_str(), ec.message().c_str());
		return -3;
	}
	return 0;
}

void notifyHashFailedAlert(JNIEnv *env, const libtorrent::hash_failed_alert *alert) {
	char ih[41];
	const libtorrent::sha1_hash &hashCode = alert->handle.info_hash();
	libtorrent::to_hex((char const*) &hashCode[0], 20, ih);
	jstring hash = env->NewStringUTF(ih);
	env->CallStaticVoidMethod(gJniObject.listenerClass, gJniObject.listenerHashPieceFailed, hash, alert->piece_index);
}
}

