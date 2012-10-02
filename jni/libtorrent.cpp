//-----------------------------------------------------------------------------
#include "com_solt_libtorrent_LibTorrent.h"
//-----------------------------------------------------------------------------
#include "libtorrent/bencode.hpp"
#include "libtorrent/session.hpp"
#include "libtorrent/alert_types.hpp"
#include "libtorrent/size_type.hpp"
#include "libtorrent/peer_id.hpp"
#include "libtorrent/escape_string.hpp"
#include "stream.hpp"
#include "concurrentqueue.h"
#include "piecedataqueue.h"
#include "jniutils.h"
#include "alerthandler.h"
#include "torrentinfo.h"

using solt::torrent_alert_handler;
#define LISTEN_PORT_MIN 49160
#define LISTEN_PORT_MAX 65534
#define SESSION_STATE_FILE ".ses_state"

#define RETURN_VOID
#define HASH_ASSERT(env, hashJString, valueIfFailed)  \
if (env->GetStringUTFLength(hashJString) < 40) { \
	env->ThrowNew(torrentException, "Exception: invalid hash code torrent"); \
	return valueIfFailed; \
}

//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------



//-----------------------------------------------------------------------------
std::map<libtorrent::sha1_hash, TorrentInfo*> gTorrents;
static libtorrent::session *gSession = NULL;
static boost::shared_mutex access;
static boost::mutex down_queue_mutex;
static boost::mutex alert_mutex;
static libtorrent::proxy_settings gProxy;
static std::string gDefaultSave;
static volatile bool gSessionState = false;
static jclass partialPiece = NULL;
static jmethodID partialPieceInit = NULL;
static jclass torrentException = NULL;
static jclass fileEntry = NULL;
static jmethodID fileEntryInit = NULL;
void gSession_init() {
	using namespace libtorrent;
	if (!gSession) {
		gSession = new libtorrent::session();
		//load session state
		std::vector<char> in;
		error_code ec;
		if (load_file(SESSION_STATE_FILE, in, ec) == 0) {
			lazy_entry e;
			if (lazy_bdecode(&in[0], &in[0] + in.size(), e) == 0)
				gSession->load_state(e);
		}
	}
}

void gSession_del() {
	if (gSession) {
		//saving session state
		using namespace libtorrent;
		entry session_state;
		gSession->save_state(session_state);
		std::vector<char> out;
		bencode(std::back_inserter(out), session_state);
		solt::SaveFile(SESSION_STATE_FILE, out);
		//delete session
		delete gSession;
		gSession = NULL;
	}
}

libtorrent::session* getGSession() {
	return gSession;
}

//-----------------------------------------------------------------------------

TorrentInfo* GetTorrentInfo(libtorrent::sha1_hash &hash) {
	TorrentInfo* result = NULL;
	std::map<libtorrent::sha1_hash, TorrentInfo*>::iterator iter =
			gTorrents.find(hash);
	if (iter != gTorrents.end()) {
		result = iter->second;
	}
	return result;
}

TorrentInfo* GetTorrentInfo(JNIEnv *env, libtorrent::sha1_hash &hash) {
	TorrentInfo* result = NULL;
	std::map<libtorrent::sha1_hash, TorrentInfo*>::iterator iter =
			gTorrents.find(hash);
	if (iter != gTorrents.end()) {
		result = iter->second;
	} else {
		env->ThrowNew(torrentException,
								"Exception: torrent handle not found");
	}
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_setSession(
		JNIEnv *env, jobject obj, jint ListenPort, jstring SavePath,
		jint UploadLimit, jint DownloadLimit) {
	jboolean result = JNI_FALSE;
	boost::unique_lock< boost::shared_mutex > lock(access);
	try {
		gSession_init();
		solt::JniToStdString(env, &gDefaultSave, SavePath);
		gSession->set_alert_mask(
				libtorrent::alert::error_notification
						| libtorrent::alert::storage_notification);

		int listenPort = 0;
		if (ListenPort > 0) {
			listenPort = ListenPort;
		} else {
			srand((unsigned int) time(NULL));
			listenPort = LISTEN_PORT_MIN + (rand() % (LISTEN_PORT_MAX - LISTEN_PORT_MIN + 1));
		}
		gSession->listen_on(std::make_pair(listenPort, listenPort + 10));
		libtorrent::session_settings settings;
		settings.user_agent = "hdplayer/" LIBTORRENT_VERSION;
		settings.active_downloads = 20;
		settings.active_seeds = 20;
		settings.active_limit = 20;
//		settings.prioritize_partial_pieces = true;
		settings.initial_picker_threshold = 0;
		gSession->set_settings(settings);
		int uploadLimit = UploadLimit;
		if (uploadLimit > 0) {
			gSession->set_upload_rate_limit(uploadLimit);
		} else {
			gSession->set_upload_rate_limit(0);
		}
		int downloadLimit = DownloadLimit;
		if (downloadLimit > 0) {
			gSession->set_download_rate_limit(downloadLimit);
		} else {
			gSession->set_download_rate_limit(0);
		}

		//init partialpieceinfo class and constructor
		jclass ppieceinfo = env->FindClass(
				"com/solt/libtorrent/PartialPieceInfo");
		partialPiece = (jclass) env->NewGlobalRef(ppieceinfo);
		env->DeleteLocalRef(ppieceinfo);
		partialPieceInit = env->GetMethodID(partialPiece,
				"<init>", "(III[I)V");
		jclass exception = env->FindClass(
				"com/solt/libtorrent/TorrentException");
		torrentException = (jclass) env->NewGlobalRef(exception);
		env->DeleteLocalRef(exception);
		jclass entry = env->FindClass("com/solt/libtorrent/FileEntry");
		fileEntry = (jclass) env->NewGlobalRef(entry);
		fileEntryInit = env->GetMethodID(fileEntry, "<init>", "(Ljava/lang/String;JJJZZZ)V");
		env->DeleteLocalRef(entry);
		LOG_DEBUG("ListenPort: %d\n", listenPort);
		LOG_DEBUG("DownloadLimit: %d\n", downloadLimit);
		LOG_DEBUG("UploadLimit: %d\n", uploadLimit);

		gSessionState = true;
	} catch (...) {
		LOG_ERR("Exception: failed to set session");
		gSession_del();
		gSessionState = false;
	}
	if (!gSessionState)
		LOG_ERR("LibTorrent.SetSession SessionState==false");
	gSessionState == true ? result = JNI_TRUE : result = JNI_FALSE;
	return result;
}
//-----------------------------------------------------------------------------



//-----------------------------------------------------------------------------
//enum proxy_type
//{
//	0 - none, // a plain tcp socket is used, and the other settings are ignored.
//	1 - socks4, // socks4 server, requires username.
//	2 - socks5, // the hostname and port settings are used to connect to the proxy. No username or password is sent.
//	3 - socks5_pw, // the hostname and port are used to connect to the proxy. the username and password are used to authenticate with the proxy server.
//	4 - http, // the http proxy is only available for tracker and web seed traffic assumes anonymous access to proxy
//	5 - http_pw // http proxy with basic authentication uses username and password
//};
//-----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_setProxy(
		JNIEnv *env, jobject obj, jint Type, jstring HostName, jint Port,
		jstring UserName, jstring Password) {
	jboolean result = JNI_FALSE;
	boost::unique_lock< boost::shared_mutex > lock(access);
	try {
		if (gSessionState) {
			int type = Type;
			if (type > 0) {
				std::string hostName;
				solt::JniToStdString(env, &hostName, HostName);
				int port = Port;
				std::string userName;
				solt::JniToStdString(env, &userName, UserName);
				std::string password;
				solt::JniToStdString(env, &password, Password);

				gProxy.type = libtorrent::proxy_settings::proxy_type(type);
				gProxy.hostname = hostName;
				gProxy.port = port;
				gProxy.username = userName;
				gProxy.password = password;

				gSession->set_proxy(gProxy);

				LOG_DEBUG("ProxyType: %d\n", type);
				LOG_DEBUG("HostName: %s\n", hostName.c_str());
				LOG_DEBUG("ProxyPort: %d\n", port);
				LOG_DEBUG("UserName: %s\n", userName.c_str());
				LOG_DEBUG("Password: %s\n", password.c_str());
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to set proxy");
		gSessionState = false;
	}
	if (!gSessionState)
		LOG_ERR("LibTorrent.SetProxy SessionState==false");
	gSessionState == true ? result = JNI_TRUE : result = JNI_FALSE;
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_setSessionOptions(
		JNIEnv *env, jobject obj, jboolean DHT, jboolean LSD, jboolean UPNP,
		jboolean NATPMP) {
	jboolean result = JNI_FALSE;
	boost::unique_lock< boost::shared_mutex > lock(access);
	try {
		if (gSessionState) {
			if (DHT == JNI_TRUE)
				gSession->start_dht();
			else
				gSession->stop_dht();
			if (LSD == JNI_TRUE)
				gSession->start_lsd();
			else
				gSession->stop_lsd();
			if (UPNP == JNI_TRUE)
				gSession->start_upnp();
			else
				gSession->stop_upnp();
			if (NATPMP == JNI_TRUE)
				gSession->start_natpmp();
			else
				gSession->stop_natpmp();

			LOG_DEBUG("LSD: %d\n", LSD);
			LOG_DEBUG("UPNP: %d\n", UPNP);
			LOG_DEBUG("NATPMP: %d\n", NATPMP);
		}
	} catch (...) {
		LOG_ERR("Exception: failed to set session options");
		gSessionState = false;
	}
	if (!gSessionState)
		LOG_ERR("LibTorrent.SetSessionOptions SessionState==false");
	gSessionState == true ? result = JNI_TRUE : result = JNI_FALSE;
	return result;
}
//-----------------------------------------------------------------------------
//StorageMode:
//0-storage_mode_allocate
//1-storage_mode_sparse
//2-storage_mode_compact
JNIEXPORT jstring JNICALL Java_com_solt_libtorrent_LibTorrent_addTorrent(
		JNIEnv *env, jobject obj, jstring TorrentFile, jint StorageMode,
		jboolean autoManaged) {
	jstring result = NULL;
	boost::unique_lock< boost::shared_mutex > lock(access);
	try {
		if (gSessionState) {
			//compute contentFile
			std::string torrentFile;
			solt::JniToStdString(env, &torrentFile, TorrentFile);

			boost::intrusive_ptr<libtorrent::torrent_info> t;
			libtorrent::error_code ec;
			t = new libtorrent::torrent_info(torrentFile.c_str(), ec);
			if (ec) {
				std::string errorMessage = ec.message();
				LOG_ERR("%s: %s\n", torrentFile.c_str(), errorMessage.c_str());
				return result;
			}
			const libtorrent::sha1_hash &hashCode = t->info_hash();
			//find torrent_handle
			std::map<libtorrent::sha1_hash, TorrentInfo*>::iterator iter =
					gTorrents.find(hashCode);
			if (iter != gTorrents.end()) {
				LOG_DEBUG(
						"Torrent file already presents: %s", torrentFile.c_str());
				char ih[41];
				libtorrent::to_hex((char const*) &hashCode[0], 20, ih);
				result = env->NewStringUTF(ih);
			} else {
				LOG_DEBUG("TorrentFile: %s", torrentFile.c_str());

				LOG_DEBUG("%s\n", t->name().c_str());
				LOG_DEBUG("StorageMode: %d\n", StorageMode);

				libtorrent::add_torrent_params torrentParams;
				libtorrent::lazy_entry resume_data;

				boost::filesystem::path save_path = gDefaultSave;
				std::string filename =
						(save_path / (t->name() + RESUME_SUFFIX)).string();
				std::vector<char> buf;
				boost::system::error_code errorCode;
				if (libtorrent::load_file(filename.c_str(), buf, errorCode)
						== 0)
					torrentParams.resume_data = &buf;

				torrentParams.ti = t;
				torrentParams.save_path = gDefaultSave;
				torrentParams.duplicate_is_error = false;
				torrentParams.auto_managed = false;
				torrentParams.upload_mode = true;
				libtorrent::storage_mode_t storageMode =
						libtorrent::storage_mode_sparse;
				switch (StorageMode) {
				case 0:
					storageMode = libtorrent::storage_mode_allocate;
					break;
				case 1:
					storageMode = libtorrent::storage_mode_sparse;
					break;
				case 2:
					storageMode = libtorrent::storage_mode_compact;
					break;
				}
				torrentParams.storage_mode = storageMode;
				TorrentInfo *torrent = new TorrentInfo();
				torrent->handle = gSession->add_torrent(torrentParams, ec);
				libtorrent::torrent_handle* th = &torrent->handle;
				if (ec) {
					std::string errorMessage = ec.message();
					delete torrent;
					LOG_ERR(
							"failed to add torrent: %s\n", errorMessage.c_str());
				} else {
					th->piece_priority(0, 7);
					int last_piece = t->num_pieces() - 1;
					th->piece_priority(last_piece, 7);
					th->set_upload_mode(false);
					if (th->is_paused()) {
						th->resume();
					}
					th->add_extension(&solt::create_stream_plugin, &torrent->cancel_piece_tasks);
					th->auto_managed(autoManaged);
					gTorrents[hashCode] = torrent;
					char ih[41];
					libtorrent::to_hex((char const*) &hashCode[0], 20, ih);
					result = env->NewStringUTF(ih);
				}

			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to add torrent");
	}
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_pauseSession(
		JNIEnv *, jobject) {
	jboolean result = JNI_FALSE;
	boost::unique_lock< boost::shared_mutex > lock(access);
	try {
		if (gSessionState) {
			gSession->pause();
			bool paused = gSession->is_paused();
			if (paused)
				result = JNI_TRUE;
		}
	} catch (...) {
		LOG_ERR("Exception: failed to pause session");
		gSessionState = false;
	}
	if (!gSessionState)
		LOG_ERR("LibTorrent.PauseSession SessionState==false");
	gSessionState == true ? result = JNI_TRUE : result = JNI_FALSE;
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_resumeSession(
		JNIEnv *, jobject) {
	jboolean result = JNI_FALSE;
	boost::unique_lock< boost::shared_mutex > lock(access);
	try {
		if (gSessionState) {
			gSession->resume();
			bool paused = gSession->is_paused();
			if (!paused)
				result = JNI_TRUE;
		}
	} catch (...) {
		LOG_ERR("Exception: failed to resume session");
		gSessionState = false;
	}
	if (!gSessionState)
		LOG_ERR("LibTorrent.ResumeSession SessionState==false");
	gSessionState == true ? result = JNI_TRUE : result = JNI_FALSE;
	return result;
}

bool saveResumeData() {
	using namespace libtorrent;
	int num_resume_data = 0;
	std::vector<torrent_handle> handles = gSession->get_torrents();
	boost::mutex::scoped_lock l(alert_mutex);
	for (std::vector<torrent_handle>::iterator i = handles.begin();
			i != handles.end(); ++i) {
		torrent_handle& h = *i;
		if (!h.has_metadata())
			continue;
		if (!h.is_valid())
			continue;

		h.save_resume_data();
		++num_resume_data;
	}

	while (num_resume_data > 0) {
		alert const* a = gSession->wait_for_alert(seconds(10));

		// if we don't get an alert within 10 seconds, abort
		if (a == 0)
			break;

		std::auto_ptr<alert> holder = gSession->pop_alert();

		if (alert_cast<save_resume_data_failed_alert>(a)) {
			//        process_alert(a);
			--num_resume_data;
			continue;
		}

		save_resume_data_alert const* rd = alert_cast<save_resume_data_alert>(
				a);
		if (rd == 0) {
			//       process_alert(a);
			continue;
		}

		torrent_handle h = rd->handle;
		if (rd->resume_data) {
			std::vector<char> out;
			libtorrent::bencode(std::back_inserter(out), *rd->resume_data);
			boost::filesystem::path savePath = h.save_path();
			savePath /= (h.name() + RESUME_SUFFIX);
			solt::SaveFile(savePath, out);
		}
		--num_resume_data;
	}
	return (num_resume_data == 0);
}

JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_saveResumeData
  (JNIEnv *, jobject) {
	bool result = false;
	boost::unique_lock< boost::shared_mutex > lock(access);
	try {
		if (gSessionState) {
			if (gSession->is_paused()) {
				result = saveResumeData();
			} else {
				gSession->pause();
				result = saveResumeData();
				gSession->resume();
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to save resume data");
		gSessionState = false;
	}
	if (!gSessionState)
		LOG_ERR("LibTorrent.SaveResumeData SessionState==false");
	return result == true ? JNI_TRUE : JNI_FALSE;
}

//-----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_abortSession(
		JNIEnv *env, jobject) {
	jboolean result = JNI_FALSE;
	boost::unique_lock< boost::shared_mutex > lock(access);
	try {
		if (gSessionState) {
			gSession->pause();
			saveResumeData();
			gSession->abort();
			gSession_del();
			//free partialpieceinfo class and constructor (dont need)
		}
	} catch (...) {
		LOG_ERR("Exception: failed to abort session");
		gSessionState = false;
	}
	if (!gSessionState)
		LOG_ERR("LibTorrent.AbortSession SessionState==false");
	gSessionState == true ? result = JNI_TRUE : result = JNI_FALSE;
	return result;
}
//-----------------------------------------------------------------------------

//-----------------------------------------------------------------------------


inline jboolean removeTorrent(libtorrent::torrent_handle* pTorrent) {
	pTorrent->auto_managed(false);
	pTorrent->pause();
	// the alert handler for save_resume_data_alert
	// will save it to disk
	solt::torrent_alert_handler alert_handler(pTorrent->info_hash(),
			torrent_alert_handler::alert_type::save_resume_data, 1);
	boost::mutex::scoped_lock l(alert_mutex);
	pTorrent->save_resume_data();
	// loop through the alert queue to see if anything has happened.
	while (alert_handler.get_num_alert() > 0) {
		libtorrent::alert const* a = gSession->wait_for_alert(
				libtorrent::seconds(10));
		// if we don't get an alert within 10 seconds, abort
		if (a == 0)
			break;
		std::auto_ptr<libtorrent::alert> holder = gSession->pop_alert();
		LOG_DEBUG("RemoveTorrent Alert: %s", a->message().c_str());
		try {
			solt::handle_remove_torrent_alert(alert_handler, a);
		} catch (libtorrent::unhandled_alert &e) {

		}
	}
	gSession->remove_torrent(*pTorrent);
	l.unlock();
	LOG_DEBUG("remove_torrent");
	return JNI_TRUE;
}

inline jboolean deleteTorrent(libtorrent::torrent_handle* pTorrent) {
	// the alert handler for save_resume_data_alert
	// will save it to disk
	boost::filesystem::path resumeFile = pTorrent->save_path().string()
			+ pTorrent->name() + RESUME_SUFFIX;
	bool del = false;
	solt::torrent_alert_handler alert_handler(pTorrent->info_hash(),
			torrent_alert_handler::alert_type::torrent_deleted, 1);
	boost::mutex::scoped_lock l(alert_mutex);
	gSession->remove_torrent(*pTorrent, libtorrent::session::delete_files);
	// loop through the alert queue to see if anything has happened.
	while (alert_handler.get_num_alert() > 0) {
		libtorrent::alert const* a = gSession->wait_for_alert(
				libtorrent::seconds(10));
		// if we don't get an alert within 10 seconds, abort
		if (a == 0)
			break;
		std::auto_ptr<libtorrent::alert> holder = gSession->pop_alert();
		LOG_DEBUG("RemoveTorrent Alert: %s", a->message().c_str());
		try {
			solt::handle_remove_torrent_alert(alert_handler, a);
			if (alert_handler.get_alert_type() == torrent_alert_handler::alert_type::torrent_deleted
					&& alert_handler.get_expected_type() == 0
					&& !alert_handler.is_error_alert()) {
				del = true;
			}
		} catch (libtorrent::unhandled_alert &e) {

		}
	}
	l.unlock();
	if (del && boost::filesystem::exists(resumeFile)) {
		boost::filesystem::remove(resumeFile);
	}

	LOG_DEBUG("remove_torrent");
	return JNI_TRUE;
}
//-----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_removeTorrent(
		JNIEnv *env, jobject obj, jstring hashCode, jboolean delData) {
	jboolean result = JNI_FALSE;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	boost::unique_lock< boost::shared_mutex > lock(access);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG("Remove torrent name %s", pTorrent->name().c_str());
				result =
						delData ?
								deleteTorrent(pTorrent) :
								removeTorrent(pTorrent);
				if (gTorrents.erase(hash) > 0) {
					delete pTorrentInfo;
				}
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to remove torrent");
		try {
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_pauseTorrent(
		JNIEnv *env, jobject obj, jstring hashCode) {
	jboolean result = JNI_FALSE;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG("Pause torrent name %s", pTorrent->name().c_str());
				pTorrent->auto_managed(false);
				pTorrent->pause();
				bool paused = pTorrent->is_paused();
				if (paused)
					result = JNI_TRUE;
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to pause torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_resumeTorrent(
		JNIEnv *env, jobject obj, jstring hashCode) {
	jboolean result = JNI_FALSE;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG("Resume torrent name %s", pTorrent->name().c_str());
				pTorrent->resume();
				pTorrent->auto_managed(true);
				bool paused = pTorrent->is_paused();
				if (!paused)
					result = JNI_TRUE;
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to resume torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentProgress(
		JNIEnv *env, jobject obj, jstring hashCode) {
	jint result = -1;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				libtorrent::torrent_status s = pTorrent->status();
				if (s.state != libtorrent::torrent_status::seeding
						&& pTorrent->has_metadata()) {
					result = 0;
					std::vector<libtorrent::size_type> file_progress;
					pTorrent->file_progress(file_progress);
					libtorrent::torrent_info const& info =
							pTorrent->get_torrent_info();
					int files_num = info.num_files();
					for (int i = 0; i < info.num_files(); ++i) {
						int progress =
								info.file_at(i).size > 0 ?
										file_progress[i] * 1000
												/ info.file_at(i).size :
										1000;
						result += progress;
					}
					result = result / files_num;
				} else if (s.state == libtorrent::torrent_status::seeding
						&& pTorrent->has_metadata())
					result = 1000;
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to progress torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jlong JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentProgressSize(
		JNIEnv *env, jobject obj, jstring hashCode, jint flags) {
	jlong result = -1;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				libtorrent::torrent_status s = pTorrent->status();
				if (s.state != libtorrent::torrent_status::seeding
						&& pTorrent->has_metadata()) {
					std::vector<libtorrent::size_type> file_progress;
					pTorrent->file_progress(file_progress, flags);
					libtorrent::torrent_info const& info =
							pTorrent->get_torrent_info();
					int files_num = info.num_files();
					long long bytes_size = 0;
					for (int i = 0; i < info.num_files(); ++i) {
						if (info.file_at(i).size > 0) {
							bytes_size += file_progress[i];
						}
					}
					if (bytes_size >= 0) {
						result = bytes_size;
					}
				} else if (s.state == libtorrent::torrent_status::seeding
						&& pTorrent->has_metadata()) {
					libtorrent::torrent_info const& info =
							pTorrent->get_torrent_info();
					long long bytes_size = info.total_size();
					if (bytes_size > 0) {
						result = bytes_size;
					}
				}
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to progress torrent size");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}



/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    getTorrentContinuousSize
 * Signature: (Ljava/lang/String;J)J
 */JNIEXPORT jlong JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentContinuousSize(
		JNIEnv *env, jobject obj, jstring hashCode, jlong offset) {
	jlong result = -1;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo* pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (!pTorrentInfo) {
				return result;
			}
			libtorrent::torrent_handle* pTorrent = &(pTorrentInfo->handle);
			libtorrent::torrent_status s = pTorrent->status();
			if (s.state != libtorrent::torrent_status::seeding
					&& pTorrent->has_metadata()) {
				if (!s.pieces.empty()) {

					libtorrent::torrent_info const& info =
							pTorrent->get_torrent_info();
					int pieceSize = info.piece_length();
					int pieceIdx = offset / pieceSize;
					boost::mutex::scoped_lock l(pTorrentInfo->cont_piece_mutex);
					bool inside = pTorrentInfo->pieceTransferIdx <= pieceIdx
							&& pieceIdx < pTorrentInfo->firstPieceIncompleteIdx;
					int i = inside ?
							pTorrentInfo->firstPieceIncompleteIdx : pieceIdx;
					int n = s.pieces.size();
					for (; i < n && s.pieces.get_bit(i); ++i) {

					}LOG_DEBUG(
							"downloaded piece at %d num_pieces = %d in total = %d", i, s.num_pieces, n);
					if (i > pieceIdx) {
						result = (pieceIdx + 1) * pieceSize - offset
								+ (i - pieceIdx - 1) * pieceSize;
						//update cont piece idx
						if (!inside) {
							pTorrentInfo->pieceTransferIdx = pieceIdx;
							pTorrentInfo->firstPieceIncompleteIdx = i;
						} else if (i > pTorrentInfo->firstPieceIncompleteIdx) {
							LOG_DEBUG( "set new firstPieceIncompleteIdx %d", i);
							pTorrentInfo->firstPieceIncompleteIdx = i;
						}
					}
				}
			} else if (s.state == libtorrent::torrent_status::seeding
					&& pTorrent->has_metadata()) {
				libtorrent::torrent_info const& info =
						pTorrent->get_torrent_info();
				long long bytes_size = info.total_size();
				if (bytes_size > 0 && bytes_size > offset) {
					result = bytes_size - offset;
				}
			}

		}
	} catch (...) {
		LOG_ERR("Exception: failed to progress continuous torrent size");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
		env->ThrowNew(torrentException,
							"Exception: failed to progress continuous torrent size");
	}
	return result;
}

 JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_setTorrentReadPiece
 	 (JNIEnv *env, jobject obj, jstring hashCode, jint pieceIdx) {
	 if (pieceIdx < 0) return;
	HASH_ASSERT(env, hashCode, RETURN_VOID);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo* pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo && pieceIdx < pTorrentInfo->handle.get_torrent_info().num_pieces()) {
				pTorrentInfo->handle.read_piece(pieceIdx);
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed when set  read piece's data");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
		env->ThrowNew(torrentException,
							"Exception: failed when set read piece's data");
	}
}

 JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_readTorrentPiece
   (JNIEnv *env, jobject obj, jstring hashCode, jint pieceIdx, jbyteArray buffer) {
	jint result = -1;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo* pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::read_piece_alert* alrt = pTorrentInfo->piece_queue.pop(pieceIdx);
				if (!alrt) {
					//try pop alert from session to get read_piece_alert
					boost::mutex::scoped_lock l(alert_mutex);
					std::auto_ptr<libtorrent::alert> a;
					a = gSession->pop_alert();
					while (a.get()){
						if (solt::handle_read_piece_alert(hash, pTorrentInfo->piece_queue, pieceIdx, a.get())) {
							alrt = libtorrent::alert_cast<libtorrent::read_piece_alert>(a.release());
							break;
						}
						a = gSession->pop_alert();
					}
				}

				if (!alrt) {
					return 0;
				} else if (alrt->buffer) {
					//copy data
					result = solt::copyPieceData(env, alrt, buffer);
				}
				delete alrt;
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to read piece's data");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
		env->ThrowNew(torrentException,
							"Exception: failed to read piece's data");
	}
	return result;
 }


/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    setDownloadRateLimit
 * Signature: (I)V
 */JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_setDownloadRateLimit(
		JNIEnv *env, jobject obj, jint DownloadLimit) {
	int downloadLimit = DownloadLimit;
	if (downloadLimit > 0) {
		gSession->set_download_rate_limit(downloadLimit);
	} else {
		gSession->set_download_rate_limit(0);
	}
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    getDownloadRateLimit
 * Signature: ()I
 */JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_getDownloadRateLimit(
		JNIEnv *env, jobject obj) {
	return gSession->download_rate_limit();
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    setUploadRateLimit
 * Signature: (I)V
 */JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_setUploadRateLimit(
		JNIEnv *env, jobject obj, jint UploadLimit) {
	int uploadLimit = UploadLimit;
	if (uploadLimit > 0) {
		gSession->set_upload_rate_limit(uploadLimit);
	} else {
		gSession->set_upload_rate_limit(0);
	}
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    getUploadRateLimit
 * Signature: ()I
 */JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_getUploadRateLimit(
		JNIEnv *env, jobject obj) {
	return gSession->upload_rate_limit();
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    setTorrentDownloadLimit
 * Signature: (Ljava/lang/String;I)V
 */JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_setTorrentDownloadLimit(
		JNIEnv *env, jobject obj, jstring hashCode, jint DownloadLimit) {
	HASH_ASSERT(env, hashCode, RETURN_VOID);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"set download limit torrent name %s", pTorrent->name().c_str());
				int downloadLimit = DownloadLimit;
				if (downloadLimit < 0) {
					downloadLimit = 0;
				}
				pTorrent->set_download_limit(downloadLimit);
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to set download limit torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    getTorrentDownloadLimit
 * Signature: (Ljava/lang/String;)I
 */JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentDownloadLimit(
		JNIEnv *env, jobject obj, jstring hashCode) {
	HASH_ASSERT(env, hashCode, -1);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"get download limit torrent name %s", pTorrent->name().c_str());
				return pTorrent->download_limit();
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get download limit torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return -1;
}


JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentDownloadRate(
		JNIEnv *env, jobject obj, jstring hashCode, jboolean payload) {
	jint result = -1;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				libtorrent::torrent_status t_s = pTorrent->status();
				result = payload ? t_s.download_payload_rate : t_s.download_rate;
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get torrent state");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}
/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    setTorrentUploadLimit
 * Signature: (Ljava/lang/String;I)V
 */JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_setTorrentUploadLimit(
		JNIEnv *env, jobject obj, jstring hashCode, jint UploadLimit) {

	HASH_ASSERT(env, hashCode, RETURN_VOID);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"set upload limit torrent name %s", pTorrent->name().c_str());
				int uploadLimit = UploadLimit;
				if (uploadLimit < 0) {
					uploadLimit = 0;
				}
				pTorrent->set_upload_limit(uploadLimit);
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to set upload limit torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    getTorrentUploadLimit
 * Signature: (Ljava/lang/String;)I
 */JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentUploadLimit(
		JNIEnv *env, jobject obj, jstring hashCode) {
	HASH_ASSERT(env, hashCode, -1);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"get upload limit torrent name %s", pTorrent->name().c_str());
				return pTorrent->upload_limit();
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get upload limit torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return -1;
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    setUploadMode
 * Signature: (Ljava/lang/String;Z)V
 */JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_setUploadMode(
		JNIEnv *env, jobject obj, jstring hashCode, jboolean uploadMode) {
	HASH_ASSERT(env, hashCode, RETURN_VOID);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"set upload mode torrent name %s", pTorrent->name().c_str());
				pTorrent->set_upload_mode(uploadMode);
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to set upload mode torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    isAutoManaged
 * Signature: (Ljava/lang/String;)Z
 */JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_isAutoManaged(
		JNIEnv *env, jobject obj, jstring hashCode) {
	HASH_ASSERT(env, hashCode, JNI_FALSE);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"check auto managed torrent name %s", pTorrent->name().c_str());
				return pTorrent->is_auto_managed();
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to check auto managed torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return JNI_FALSE;
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    setAutoManaged
 * Signature: (Ljava/lang/String;Z)V
 */JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_setAutoManaged(
		JNIEnv *env, jobject obj, jstring hashCode, jboolean isAuto) {
	HASH_ASSERT(env, hashCode, RETURN_VOID);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"set auto managed torrent name %s", pTorrent->name().c_str());
				pTorrent->auto_managed(isAuto);
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to set auto managed torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
}

 JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_getPieceNum
   (JNIEnv *env, jobject obj, jstring hashCode) {
	jint result = -1;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"get piece priority torrent name %s", pTorrent->name().c_str());
				result = pTorrent->get_torrent_info().num_pieces();
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get piece num");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
 }

 JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_getPieceSize
 (JNIEnv *env, jobject obj, jstring hashCode, jboolean isLast) {
	jint result = -1;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"get piece priority torrent name %s", pTorrent->name().c_str());
				libtorrent::torrent_info const& info =
											pTorrent->get_torrent_info();
				if (isLast) {
					result = info.total_size() - info.piece_length() * (info.num_pieces() - 1);
				} else {
					result = info.piece_length();
				}
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get piece num");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
 }

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    getFirstPieceIncomplete
 * Signature: (Ljava/lang/String;J)I
 */JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_getFirstPieceIncomplete(
		JNIEnv *env, jobject obj, jstring hashCode, jlong offset) {
	jint result = -1;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo* pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &(pTorrentInfo->handle);
				libtorrent::torrent_status s = pTorrent->status();
				if (s.state != libtorrent::torrent_status::seeding
						&& pTorrent->has_metadata()) {
					if (!s.pieces.empty()) {

						libtorrent::torrent_info const& info =
								pTorrent->get_torrent_info();
						int pieceSize = info.piece_length();
						int pieceIdx = offset / pieceSize;
						boost::mutex::scoped_lock l(pTorrentInfo->cont_piece_mutex);
						bool inside = pTorrentInfo->pieceTransferIdx <= pieceIdx
								&& pieceIdx
										< pTorrentInfo->firstPieceIncompleteIdx;
						int i = inside ?
								pTorrentInfo->firstPieceIncompleteIdx : pieceIdx;

						int n = s.pieces.size();
						for (; i < n && s.pieces.get_bit(i); ++i) {

						}LOG_DEBUG(
								"downloaded piece at %d num_pieces = %d in total = %d", i, s.num_pieces, n);
						result = i;
						if (i > pieceIdx) {
							if (!inside) {
								pTorrentInfo->pieceTransferIdx = pieceIdx;
								pTorrentInfo->firstPieceIncompleteIdx = i;
							} else if (i
									> pTorrentInfo->firstPieceIncompleteIdx) {
								LOG_DEBUG("set new firstPieceIncompleteIdx %d", i);
								pTorrentInfo->firstPieceIncompleteIdx = i;
							}
						}

					}
				} else if (s.state == libtorrent::torrent_status::seeding
						&& pTorrent->has_metadata()) {
					libtorrent::torrent_info const& info =
							pTorrent->get_torrent_info();
					result = info.num_pieces();
				}
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get first piece incomplete");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    setPiecePriority
 * Signature: (Ljava/lang/String;II)V
 */JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_setPiecePriority(
		JNIEnv *env, jobject obj, jstring hashCode, jint pieceIdx,
		jint priorityLevel) {
	HASH_ASSERT(env, hashCode, RETURN_VOID);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"set piece priority torrent name %s", pTorrent->name().c_str());
				pTorrent->piece_priority(pieceIdx, priorityLevel);
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to set piece priority torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    getPiecePriority
 * Signature: (Ljava/lang/String;I)I
 */JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_getPiecePriority(
		JNIEnv *env, jobject obj, jstring hashCode, jint pieceIdx) {
	jint result = -1;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"get piece priority torrent name %s", pTorrent->name().c_str());
				result = pTorrent->piece_priority(pieceIdx);
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get piece priority torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    setPiecePriorities
 * Signature: (Ljava/lang/String;[B)V
 */JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_setPiecePriorities(
		JNIEnv *, jobject, jstring, jbyteArray);
/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    getPiecePriorities
 * Signature: (Ljava/lang/String;)[B
 */JNIEXPORT jbyteArray JNICALL Java_com_solt_libtorrent_LibTorrent_getPiecePriorities(
		JNIEnv *, jobject, jstring);

JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_setPieceDeadline(
		JNIEnv *env, jobject obj, jstring hashCode, jint pieceIdx,
		jint deadline, jboolean readPiece) {
	if (pieceIdx < 0) {
		return;
	}
	HASH_ASSERT(env, hashCode, RETURN_VOID);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo && pieceIdx < pTorrentInfo->handle.get_torrent_info().num_pieces()) {
				LOG_DEBUG(
						"set piece deadline torrent name %s", pTorrent->name().c_str());
				int flag = readPiece ? libtorrent::torrent_handle::alert_when_available : 0;
				pTorrentInfo->handle.set_piece_deadline(pieceIdx, deadline, flag);
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to set piece deadline torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    resetPieceDeadline
 * Signature: (Ljava/lang/String;I)V
 */JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_resetPieceDeadline(
		JNIEnv *env, jobject obj, jstring hashCode, jint pieceIdx) {
	HASH_ASSERT(env, hashCode, RETURN_VOID);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"reset piece deadline torrent name %s", pTorrent->name().c_str());
				pTorrent->reset_piece_deadline(pieceIdx);
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to reset piece deadline torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    clearPiecesDeadline
 * Signature: (Ljava/lang/String;)V
 */JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_clearPiecesDeadline(
		JNIEnv *env, jobject obj, jstring hashCode) {
	HASH_ASSERT(env, hashCode, RETURN_VOID);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"clear piece deadline torrent name %s", pTorrent->name().c_str());
#ifdef LIBTORRENT_CUSTOME
				pTorrent->clear_pieces_deadline();
#endif
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to clear piece deadline torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
}

 JNIEXPORT void JNICALL Java_com_solt_libtorrent_LibTorrent_cancelTorrentPiece
 	 (JNIEnv *env, jobject obj, jstring hashCode, jint pieceIdx) {
	 HASH_ASSERT(env, hashCode, RETURN_VOID);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				pTorrentInfo->cancel_piece_tasks.push(pieceIdx);
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to cancel piece torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
 }



/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    initPartialPiece
 * Signature: (Ljava/lang/String;I[I)[I
 */JNIEXPORT jintArray JNICALL Java_com_solt_libtorrent_LibTorrent_initPartialPiece(
		JNIEnv *env, jobject obj, jstring hashCode, jint pieceIdx,
		jintArray fields) {
	HASH_ASSERT(env, hashCode, NULL);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"init piece partial piece torrent name %s", pTorrent->name().c_str());
				std::vector<libtorrent::partial_piece_info> queue;
				boost::mutex::scoped_lock l(down_queue_mutex);
				pTorrent->get_download_queue(queue);
				for (std::vector<libtorrent::partial_piece_info>::iterator
						iter = queue.begin(), end = queue.end(); iter != end;
						++iter) {
					if (iter->piece_index == pieceIdx) {
						//set info
						jboolean isCopy = JNI_FALSE;
						jint* pInfo = env->GetIntArrayElements(fields, &isCopy);
						pInfo[0] = iter->piece_state;
						pInfo[1] = iter->blocks_in_piece;
						env->ReleaseIntArrayElements(fields, pInfo, 0);
						// allocate block's data
						return solt::blocksToArray(env, iter->blocks_in_piece,
								iter->blocks);
					}
				}
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to init piece partial piece");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return NULL;
}

/*
 * Class:     com_solt_libtorrent_LibTorrent
 * Method:    getPieceDownloadQueue
 * Signature: (Ljava/lang/String;)[Lcom/solt/libtorrent/PartialPieceInfo;
 */JNIEXPORT jobjectArray JNICALL Java_com_solt_libtorrent_LibTorrent_getPieceDownloadQueue(
		JNIEnv *env, jobject obj, jstring hashCode) {
	HASH_ASSERT(env, hashCode, NULL);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG(
						"get piece download queue torrent name %s", pTorrent->name().c_str());
				std::vector<libtorrent::partial_piece_info> queue;
				boost::mutex::scoped_lock l(down_queue_mutex);
				pTorrent->get_download_queue(queue);
				if (queue.empty()) {
					return NULL;
				}
				jobjectArray pieces = env->NewObjectArray(queue.size(),
						partialPiece, NULL);
				int i = 0;
				for (std::vector<libtorrent::partial_piece_info>::iterator
						iter = queue.begin(), end = queue.end(); iter != end;
						++iter) {
					//create new PartialPieceInfo object
					jintArray blocks = solt::blocksToArray(env, iter->blocks_in_piece,
							iter->blocks);
					jobject piece = env->NewObject(partialPiece,
							partialPieceInit, iter->piece_index,
							iter->piece_state, iter->blocks_in_piece, blocks);
					env->SetObjectArrayElement(pieces, i, piece);
					++i;
				}
				return pieces;
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get piece download queue");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return NULL;

}

//-----------------------------------------------------------------------------
//enum state_t
//{
//	0 queued_for_checking,
//	1 checking_files,
//	2 downloading_metadata,
//	3 downloading,
//	4 finished,
//	5 seeding,
//	6 allocating,
//	7 checking_resume_data
//}
// + 8 paused
// + 9 queued
//-----------------------------------------------------------------------------
JNIEXPORT jint JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentState(
		JNIEnv *env, jobject, jstring hashCode) {
	jint result = -1;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				libtorrent::torrent_status t_s = pTorrent->status();
				bool paused = pTorrent->is_paused();
				bool auto_managed = pTorrent->is_auto_managed();
				if (paused && auto_managed)
					result = 8; //paused
				else if (paused && !auto_managed)
					result = 9; //queued
				else
					result = t_s.state;
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get torrent state");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}
//-----------------------------------------------------------------------------
std::string add_suffix(float val, char const* suffix = 0) {
	std::string ret;
	if (val == 0) {
		ret.resize(4 + 2, ' ');
		if (suffix)
			ret.resize(4 + 2 + strlen(suffix), ' ');
		return ret;
	}

	const char* prefix[] = { "kB", "MB", "GB", "TB" };
	const int num_prefix = sizeof(prefix) / sizeof(const char*);
	char temp[30];
	for (int i = 0; i < num_prefix; ++i) {
		val /= 1000.f;
		if (std::fabs(val) < 1000.f) {
			memset(temp, 0, 30);
			sprintf(temp, "%.4g", val);
			ret = temp;
			ret += prefix[i];
			if (suffix)
				ret += suffix;
			return ret;
		}
	}
	memset(temp, 0, 30);
	sprintf(temp, "%.4g", val);
	ret = temp;
	ret += "PB";
	if (suffix)
		ret += suffix;
	return ret;
}
//-----------------------------------------------------------------------------
static char const* state_str[] = { "checking (q)", "checking", "dl metadata",
		"downloading", "finished", "seeding", "allocating", "checking (r)" };
//-----------------------------------------------------------------------------
JNIEXPORT jstring JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentStatusText(
		JNIEnv *env, jobject obj, jstring hashCode) {
	jstring result = NULL;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				std::string out;
				char str[500];
				memset(str, 0, 500);
				//------- NAME --------
				//std::string name = gTorrent.name();
				//if (name.size() > 80) name.resize(80);
				//snprintf(str, sizeof(str), "%-80s\n", name.c_str());
				//out += str;
				out += pTorrent->name();
				out += "\n";
				//------- ERROR --------
				libtorrent::torrent_status t_s = pTorrent->status();
				bool paused = pTorrent->is_paused();
				bool auto_managed = pTorrent->is_auto_managed();
				if (!t_s.error.empty()) {
					out += "error ";
					out += t_s.error;
					out += "\n";
					return env->NewStringUTF(out.c_str());
				}
				//---------------------
				int seeds = 0;
				int downloaders = 0;
				if (t_s.num_complete >= 0)
					seeds = t_s.num_complete;
				else
					seeds = t_s.list_seeds;
				if (t_s.num_incomplete >= 0)
					downloaders = t_s.num_incomplete;
				else
					downloaders = t_s.list_peers - t_s.list_seeds;
				//---------------------
				if (t_s.state != libtorrent::torrent_status::queued_for_checking
						&& t_s.state
								!= libtorrent::torrent_status::checking_files) {
					snprintf(str, sizeof(str), "%26s%20s/%20s\n"
							"%26s%20s/%s\n"
							"%20s%20d/%d\n"
							"%17s%20d/%d\n"
							"%23s%20s/%s\n"
							"%23s%20x\n", "down/redundant:",
							add_suffix(t_s.total_download).c_str(), add_suffix(t_s.total_redundant_bytes).c_str(),
							"up/rate:",
							add_suffix(t_s.total_upload).c_str(),
							add_suffix(t_s.upload_rate, "/s").c_str(),
							"downs/seeds:", downloaders, seeds,
							"queue up/down:", t_s.up_bandwidth_queue,
							t_s.down_bandwidth_queue, "all-time rx/tx:",
							add_suffix(t_s.all_time_download).c_str(),
							add_suffix(t_s.all_time_upload).c_str(),
							"seed rank:", t_s.seed_rank);
					out += str;
					boost::posix_time::time_duration t = t_s.next_announce;
					snprintf(str, sizeof(str), "%22s%20d/%d\n"
							"%26s%20d\n"
							"%26s%20.2f\n"
							"%25s%20d\n"
							"%22s%20s\n"
							"%22s%20.02d:%02d:%02d\n"
							"%26s%s\n", "peers/cand:", t_s.num_peers,
							t_s.connect_candidates, "seeds:", t_s.num_seeds,
							"copies:", t_s.distributed_copies, "regions:",
							t_s.sparse_regions, "download:",
							add_suffix(t_s.download_rate, "/s").c_str(),
							"announce:", t.hours(), t.minutes(), t.seconds(),
							"tracker:", t_s.current_tracker.c_str());
					out += str;
				}
				result = env->NewStringUTF(out.c_str());
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get torrent status text");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jstring JNICALL Java_com_solt_libtorrent_LibTorrent_getSessionStatusText(
		JNIEnv *env, jobject obj) {
	jstring result = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			std::string out;
			char str[500];
			memset(str, 0, 500);
			libtorrent::session_status s_s = gSession->status();
			snprintf(str, sizeof(str), "%25s%20d\n"
					"%22s%20s/%s\n"
					"%25s%20s/%s\n"
					"%18s%20s/%s\n"
					"%30s%20d/%d\n"
					"%15s%20s/%s\n"
					"%19s%20s/%s\n", "conns:", s_s.num_peers, "down/rate:",
					add_suffix(s_s.total_download).c_str(),
					add_suffix(s_s.download_rate, "/s").c_str(), "up/rate:",
					add_suffix(s_s.total_upload).c_str(),
					add_suffix(s_s.upload_rate, "/s").c_str(),
					"ip rate down/up:",
					add_suffix(s_s.ip_overhead_download_rate, "/s").c_str(),
					add_suffix(s_s.ip_overhead_upload_rate, "/s").c_str(),
					"dht nodes/ dht node cache:", s_s.dht_nodes, s_s.dht_node_cache,
					"dht rate down/up:",
					add_suffix(s_s.dht_download_rate, "/s").c_str(),
					add_suffix(s_s.dht_upload_rate, "/s").c_str(),
					"tr rate down/up:",
					add_suffix(s_s.tracker_download_rate, "/s").c_str(),
					add_suffix(s_s.tracker_upload_rate, "/s").c_str());
			out += str;
			result = env->NewStringUTF(out.c_str());
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get session status");
	}
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jobjectArray JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentFiles(
		JNIEnv *env, jobject obj, jstring hashCode) {
	jobjectArray result = NULL;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				if (pTorrent->has_metadata()) {
					libtorrent::torrent_info const& info =
							pTorrent->get_torrent_info();
					int files_num = info.num_files();
					result = env->NewObjectArray(files_num, fileEntry, NULL);
					jobject entry = NULL;
					for (int i = 0; i < info.num_files(); ++i) {
						libtorrent::file_entry const& file = info.file_at(i);
						jstring path = env->NewStringUTF(file.path.string().c_str());
						jboolean execAttr = file.executable_attribute ? JNI_TRUE : JNI_FALSE;
						jboolean hiddenAttr = file.hidden_attribute ? JNI_TRUE : JNI_FALSE;
						jboolean padFile = file.pad_file ? JNI_TRUE : JNI_FALSE;
						entry = env->NewObject(fileEntry, fileEntryInit, path, file.offset, file.size, file.file_base, padFile, hiddenAttr, execAttr);
						env->SetObjectArrayElement(result, i, entry);
					}
				}
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get torrent files");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}
//-----------------------------------------------------------------------------
//0 - piece is not downloaded at all
//1 - normal priority. Download order is dependent on availability
//2 - higher than normal priority. Pieces are preferred over pieces with the same availability, but not over pieces with lower availability
//3 - pieces are as likely to be picked as partial pieces.
//4 - pieces are preferred over partial pieces, but not over pieces with lower availability
//5 - currently the same as 4
//6 - piece is as likely to be picked as any piece with availability 1
//7 - maximum priority, availability is disregarded, the piece is preferred over any other piece with lower priority
JNIEXPORT jboolean JNICALL Java_com_solt_libtorrent_LibTorrent_setTorrentFilesPriority(
		JNIEnv *env, jobject obj, jbyteArray FilesPriority, jstring hashCode) {
	jboolean result = JNI_FALSE;
	jbyte* filesPriority = NULL;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				std::string out;
				libtorrent::torrent_status s = pTorrent->status();
				if (pTorrent->has_metadata()) {
					libtorrent::torrent_info const& info =
							pTorrent->get_torrent_info();
					int files_num = info.num_files();
					jsize arr_size = env->GetArrayLength(FilesPriority);
					if (files_num == arr_size) {
						filesPriority = env->GetByteArrayElements(FilesPriority,
								0);
						const unsigned char* prioritiesBytes =
								(const unsigned char*) filesPriority;
						std::vector<int> priorities;
						for (int i = 0; i < info.num_files(); ++i) {
							priorities.push_back(int(filesPriority[i]));
						}
						pTorrent->prioritize_files(priorities);
						result = JNI_TRUE;
					} else {
						LOG_ERR(
								"LibTorrent.SetTorrentFilesPriority priority array size failed");
					}
				}
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to set files priority");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	if (filesPriority)
		env->ReleaseByteArrayElements(FilesPriority, filesPriority, JNI_ABORT);
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jbyteArray JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentFilesPriority(
		JNIEnv *env, jobject obj, jstring hashCode) {
	jbyteArray result = NULL;
	jbyte* result_array = NULL;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				libtorrent::torrent_status s = pTorrent->status();
				if (pTorrent->has_metadata()) {
					libtorrent::torrent_info const& info =
							pTorrent->get_torrent_info();
					int files_num = info.num_files();
					std::vector<int> priorities = pTorrent->file_priorities();
					if (files_num == priorities.size()) {
						result_array = new jbyte[files_num];
						for (int i = 0; i < files_num; i++)
							result_array[i] = (jbyte) priorities[i];
						result = env->NewByteArray(files_num);
						env->SetByteArrayRegion(result, 0, files_num,
								result_array);
					} else {
						LOG_ERR(
								"LibTorrent.GetTorrentFilesPriority priority array size failed");
					}
				}
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get files priority");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			gTorrents.erase(hash);
		} catch (...) {
		}
	}
	if (result_array)
		delete[] result_array;
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jstring JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentName(
		JNIEnv *env, jobject obj, jstring hashCode) {
	jstring result = NULL;
	HASH_ASSERT(env, hashCode, result);
	libtorrent::sha1_hash hash;
	solt::JStringToHash(env, hash, hashCode);
	TorrentInfo *pTorrentInfo = NULL;
	try {
		if (gSessionState) {
			boost::shared_lock< boost::shared_mutex > lock(access);
			pTorrentInfo = GetTorrentInfo(env, hash);
			if (pTorrentInfo) {
				libtorrent::torrent_handle* pTorrent = &pTorrentInfo->handle;
				LOG_DEBUG("get torrent name %s", pTorrent->name().c_str());
				result = env->NewStringUTF(pTorrent->name().c_str());
			}
		}
	} catch (...) {
		LOG_ERR("Exception: failed to pause torrent");
		try {
			boost::unique_lock< boost::shared_mutex > lock(access);
			if (pTorrentInfo != NULL && gTorrents.erase(hash) > 0) {
				delete pTorrentInfo;
			}
		} catch (...) {
		}
	}
	return result;
}
//-----------------------------------------------------------------------------
JNIEXPORT jlong JNICALL Java_com_solt_libtorrent_LibTorrent_getTorrentSize(
		JNIEnv *env, jobject obj, jstring TorrentFile) {
	jlong result = -1;
	try {
		std::string torrentFile;
		solt::JniToStdString(env, &torrentFile, TorrentFile);

		boost::intrusive_ptr<libtorrent::torrent_info> info;
		libtorrent::error_code ec;
		info = new libtorrent::torrent_info(torrentFile.c_str(), ec);
		if (ec) {
			std::string errorMessage = ec.message();
			LOG_ERR("%s: %s\n", torrentFile.c_str(), errorMessage.c_str());
		} else {
			long long bytes_size = 0;
			bytes_size = info->total_size();
			if (bytes_size > 0)
				result = bytes_size;
		}
	} catch (...) {
		LOG_ERR("Exception: failed to get torrent size");
	}
	return result;
}
//-----------------------------------------------------------------------------

