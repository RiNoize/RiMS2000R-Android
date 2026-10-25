#pragma once
#include <cerrno>
#include <pthread.h>
#include <sys/resource.h>

namespace rims {
struct ThreadPolicy { int niceValue; int error; };
inline ThreadPolicy audioThreadPolicy(const char* name) {
    (void)pthread_setname_np(pthread_self(), name);
    errno = 0;
    const int result = setpriority(PRIO_PROCESS, 0, -16);
    const int error = result == 0 ? 0 : errno;
    errno = 0;
    const int actual = getpriority(PRIO_PROCESS, 0);
    return {actual, error};
}
}
