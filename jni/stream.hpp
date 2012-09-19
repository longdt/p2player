/*
 * stream.hpp
 *
 *  Created on: May 8, 2012
 *      Author: user
 */

#ifndef STREAM_HPP_
#define STREAM_HPP_

#ifdef _MSC_VER
#pragma warning(push, 1)
#endif

#include <boost/shared_ptr.hpp>
#include "libtorrent/config.hpp"


#ifdef _MSC_VER
#pragma warning(pop)
#endif

namespace libtorrent
{
	struct torrent_plugin;
	class torrent;
}
namespace solt {
	using namespace libtorrent;
	TORRENT_EXPORT boost::shared_ptr<torrent_plugin> create_stream_plugin(torrent*, void*);
}
#endif /* STREAM_HPP_ */
