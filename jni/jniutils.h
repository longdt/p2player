/*
 * jniutils.h
 *
 *  Created on: May 17, 2012
 *      Author: user
 */

#ifndef JNIUTILS_H_
#define JNIUTILS_H_
#include <jni.h>
#include <vector>
#include "libtorrent/peer_id.hpp"
#include "libtorrent/torrent_handle.hpp"
#include "libtorrent/alert_types.hpp"

#ifdef ANDROID
#include <android/log.h>
#define LOG_TAG "TORRENT"
#define LOG_INFO(...) {__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__);}
#define LOG_ERR(...) {__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__);}
#else
#define LOG_INFO(...) { printf(__VA_ARGS__); printf("\n"); fflush(stdout);}
#define LOG_ERR(...) { fprintf(stderr, __VA_ARGS__); fprintf(stderr, "\n"); fflush(stderr);}
#define LOG_DEBUG(...)
//{ printf(__VA_ARGS__); printf("\n"); fflush(stdout);}
#endif

namespace solt {
#define RESUME ".resume"

class jniobject {
public:
	jclass partialPiece;
	jmethodID partialPieceInit;
	jclass torrentException;
	jclass fileEntry;
	jmethodID fileEntryInit;
	jclass listenerClass;
	jmethodID listenerHashPieceFailed;

	jniobject() : partialPiece(NULL), partialPieceInit(NULL), torrentException(NULL), fileEntry(NULL), fileEntryInit(NULL), listenerClass(NULL), listenerHashPieceFailed(NULL) {}
	void init(JNIEnv *env) {
		//init partialpieceinfo class and constructor
		jclass ppieceinfo = env->FindClass(
				"com/solt/libtorrent/PartialPieceInfo");
		partialPiece = (jclass) env->NewGlobalRef(ppieceinfo);
		env->DeleteLocalRef(ppieceinfo);
		partialPieceInit = env->GetMethodID(partialPiece,
				"<init>", "(III[I)V");
		//init TorrentException jclass
		jclass exception = env->FindClass(
				"com/solt/libtorrent/TorrentException");
		torrentException = (jclass) env->NewGlobalRef(exception);
		env->DeleteLocalRef(exception);
		//init FileEntry jclass, jmethod
		jclass entry = env->FindClass("com/solt/libtorrent/FileEntry");
		fileEntry = (jclass) env->NewGlobalRef(entry);
		fileEntryInit = env->GetMethodID(fileEntry, "<init>", "(Ljava/lang/String;JJJZZZ)V");
		env->DeleteLocalRef(entry);
		//init NativeTorrentListener jmethod
		jclass listenerCl = env->FindClass("com/solt/libtorrent/LibTorrent$NativeTorrentListener");;
		listenerClass = (jclass) env->NewGlobalRef(listenerCl);
		listenerHashPieceFailed = env->GetStaticMethodID(listenerClass, "hashPieceFailed", "(Ljava/lang/String;I)V");
		env->DeleteLocalRef(listenerCl);
	}

	void release(JNIEnv *env) {
		//free partialpieceinfo class and constructor (need)
		env->DeleteGlobalRef(partialPiece);
		partialPiece = NULL;
		partialPieceInit = NULL;
		env->DeleteGlobalRef(torrentException);
		torrentException = NULL;
		env->DeleteGlobalRef(fileEntry);
		fileEntry = NULL;
		fileEntryInit = NULL;
		env->DeleteGlobalRef(listenerClass);
		listenerClass = NULL;
		listenerHashPieceFailed = NULL;
	}
};

int SaveFile(std::string const& filename, std::vector<char>& v);

void notifyHashFailedAlert(JNIEnv *env, const libtorrent::hash_failed_alert *alert);

inline void JStringToHash(JNIEnv *env, libtorrent::sha1_hash &hash,
		jstring JniString) {
	if (JniString) {
		jboolean isCopy = false;
		const char* ch = env->GetStringUTFChars(JniString, &isCopy);
		libtorrent::from_hex(ch, 40, (char*) &hash[0]);
		env->ReleaseStringUTFChars(JniString, ch);
	}
}

inline void JniToStdString(JNIEnv *env, std::string* StdString, jstring JniString) {
	if (JniString) {
		StdString->clear();
		jboolean isCopy = false;
		const char* ch = env->GetStringUTFChars(JniString, &isCopy);
		int chLen = env->GetStringUTFLength(JniString);
		for (int i = 0; i < chLen; i++)
			StdString->push_back(ch[i]);
		env->ReleaseStringUTFChars(JniString, ch);
	}
}

inline jintArray blocksToArray(JNIEnv *env, int blockSize,
		libtorrent::block_info *pData) {
	// allocate block's data
	jboolean isCopy = JNI_FALSE;
	jintArray blocks = env->NewIntArray(blockSize * 4);
	jint* pOrgBlocks = env->GetIntArrayElements(blocks, &isCopy);
	jint* pBlocks = pOrgBlocks;
	for (int i = 0; i < blockSize; ++i) {
		*pBlocks = pData->state;
		++pBlocks;
		*pBlocks = pData->bytes_progress;
		++pBlocks;
		*pBlocks = pData->block_size;
		++pBlocks;
		*pBlocks = pData->num_peers;
		++pBlocks;
		++pData;
	}
	env->ReleaseIntArrayElements(blocks, pOrgBlocks, 0);
	return blocks;
}

inline jint copyPieceData(JNIEnv *env, libtorrent::read_piece_alert* alrt, jbyteArray buffer) {
	jint len = env->GetArrayLength(buffer);
	if (len > alrt->size) {
		len = alrt->size;
	}
	jbyte* pOrgBuffer = env->GetByteArrayElements(buffer, NULL);
	jbyte* pBuffer = pOrgBuffer;
	char* pieceData = alrt->buffer.get();
	for (int i = 0; i < len; ++i) {
		*pBuffer = *pieceData;
		++pBuffer;
		++pieceData;
	}
	env->ReleaseByteArrayElements(buffer, pOrgBuffer, 0);
	return len;
}

inline int hash_value(libtorrent::sha1_hash &hash) {
	int result = 0;
	unsigned char* iter = hash.begin();
	unsigned char*end = hash.end();
	for (; iter != end; ++iter) {
		result = result * 31 + (*iter);
	}
	return result;
}

}

#endif /* JNIUTILS_H_ */
