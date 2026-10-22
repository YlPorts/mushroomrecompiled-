/* Optional Android 12+ scheduler hints. API 26 devices keep the normal path. */
#include "performance_hint.h"
#include <android/api-level.h>
#include <android/performance_hint.h>
#include <dlfcn.h>
#include <unistd.h>

static void *library;
static APerformanceHintSession *session;
static int (*report_work)(APerformanceHintSession *,int64_t);
static void (*close_session)(APerformanceHintSession *);
static unsigned reports;
void performance_hint_open(int64_t budget_ns) {
    if(android_get_device_api_level()<31)return;
    library=dlopen("libandroid.so",RTLD_NOW|RTLD_LOCAL);if(!library)return;
    APerformanceHintManager *(*manager)(void)=dlsym(library,"APerformanceHint_getManager");
    APerformanceHintSession *(*create)(APerformanceHintManager *,const int32_t *,size_t,int64_t)=dlsym(library,"APerformanceHint_createSession");
    report_work=dlsym(library,"APerformanceHint_reportActualWorkDuration");
    close_session=dlsym(library,"APerformanceHint_closeSession");
    if(manager&&create&&report_work&&close_session){APerformanceHintManager *m=manager();int32_t thread=gettid();if(m)session=create(m,&thread,1,budget_ns);}
    reports=0;
}
void performance_hint_report(int64_t work_ns) {
    if(session&&work_ns>0&&++reports%4==0)report_work(session,work_ns);
}
void performance_hint_close(void) {
    if(session)close_session(session);session=NULL;
    if(library)dlclose(library);library=NULL;
}
