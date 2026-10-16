#include <dlfcn.h>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <cstdint>
#include <vector>
#include <cmath>
using cl_int=int;
using cl_uint=unsigned;
using cl_ulong=unsigned long long;
using cl_device_type=cl_ulong;
using cl_mem_flags=cl_ulong;
using cl_command_queue_properties=cl_ulong;
using cl_platform_id=void*;
using cl_device_id=void*;
using cl_context=void*;
using cl_command_queue=void*;
using cl_program=void*;
using cl_kernel=void*;
using cl_mem=void*;
using cl_event=void*;
using cl_context_properties=intptr_t;
using PFN_platform=cl_int(*)(cl_uint,cl_platform_id*,cl_uint*);
using PFN_device=cl_int(*)(cl_platform_id,cl_device_type,cl_uint,cl_device_id*,cl_uint*);
using PFN_info=cl_int(*)(cl_device_id,cl_uint,size_t,void*,size_t*);
using PFN_context=cl_context(*)(const cl_context_properties*,cl_uint,const cl_device_id*,void(*)(const char*,const void*,size_t,void*),void*,cl_int*);
using PFN_queue=cl_command_queue(*)(cl_context,cl_device_id,cl_command_queue_properties,cl_int*);
using PFN_buffer=cl_mem(*)(cl_context,cl_mem_flags,size_t,void*,cl_int*);
using PFN_program=cl_program(*)(cl_context,cl_uint,const char**,const size_t*,cl_int*);
using PFN_build=cl_int(*)(cl_program,cl_uint,const cl_device_id*,const char*,void(*)(cl_program,void*),void*);
using PFN_buildInfo=cl_int(*)(cl_program,cl_device_id,cl_uint,size_t,void*,size_t*);
using PFN_kernel=cl_kernel(*)(cl_program,const char*,cl_int*);
using PFN_arg=cl_int(*)(cl_kernel,cl_uint,size_t,const void*);
using PFN_enqueue=cl_int(*)(cl_command_queue,cl_kernel,cl_uint,const size_t*,const size_t*,const size_t*,cl_uint,const cl_event*,cl_event*);
using PFN_read=cl_int(*)(cl_command_queue,cl_mem,cl_uint,size_t,size_t,void*,cl_uint,const cl_event*,cl_event*);
using PFN_finish=cl_int(*)(cl_command_queue);
using PFN_release=cl_int(*)(void*);
#define RESOLVE(name,T) auto name=reinterpret_cast<T>(symbol(#name));if(!name){printf("missing OpenCL symbol %s\n",#name);return 3;}
int main(){
 void* lib=nullptr;bool wrapper=false;
 for(const char* name:{"libOpenCL.so","libOpenCL-pixel.so"}){
   lib=dlopen(name,RTLD_NOW|RTLD_LOCAL);
   if(!lib) {printf("dlopen(%s) failed: %s\n",name,dlerror());continue;}
   auto load=reinterpret_cast<void*(*)(const char*)>(dlsym(lib,"loadOpenCLPointer"));
   auto enable=reinterpret_cast<void(*)()>(dlsym(lib,"enableOpenCL"));
   wrapper=enable&&load;
   if(wrapper) {enable();printf("enabled Pixel OpenCL wrapper %s\n",name);}
   else printf("loaded OpenCL symbols via %s\n",name);
   auto get=wrapper?load("clGetPlatformIDs"):dlsym(lib,"clGetPlatformIDs");
   if(get)break;
   printf("no clGetPlatformIDs in %s\n",name);dlclose(lib);lib=nullptr;
 }
 if(!lib) {puts("OpenCL runtime was not dynamically loadable");return 2;}
 auto symbol=[&](const char*n)->void*{
  if(wrapper) return reinterpret_cast<void*(*)(const char*)>(dlsym(lib,"loadOpenCLPointer"))(n);
  return dlsym(lib,n);
 };
 RESOLVE(clGetPlatformIDs,PFN_platform);
 RESOLVE(clGetDeviceIDs,PFN_device);
 RESOLVE(clGetDeviceInfo,PFN_info);
 cl_uint np=0;cl_int rc=clGetPlatformIDs(0,nullptr,&np);
 printf("platform query rc=%d count=%u\n",rc,np);
 if(rc||!np)return 4;
 std::vector<cl_platform_id> platforms(np);
 rc=clGetPlatformIDs(np,platforms.data(),nullptr);
 if(rc)return 5;
 cl_device_id selected=nullptr;
 for(auto p:platforms){
   cl_uint nd=0;rc=clGetDeviceIDs(p,4,0,nullptr,&nd);
   printf("GPU device query rc=%d count=%u\n",rc,nd);
   if(!rc&&nd){clGetDeviceIDs(p,4,1,&selected,nullptr);break;}
 }
 if(!selected)return 6;
 for(auto pair: {std::pair<unsigned,const char*>{0x102B,"name"},{0x102C,"vendor"},{0x102F,"version"},{0x1030,"extensions"}}){
   char b[4096]={};rc=clGetDeviceInfo(selected,pair.first,sizeof b,b,nullptr);if(!rc)printf("%s=%.*s\n",pair.second,pair.first==0x1030?320:300,b);
 }
 RESOLVE(clCreateContext,PFN_context);
 RESOLVE(clCreateCommandQueue,PFN_queue);
 RESOLVE(clCreateBuffer,PFN_buffer);
 RESOLVE(clCreateProgramWithSource,PFN_program);
 RESOLVE(clBuildProgram,PFN_build);
 RESOLVE(clGetProgramBuildInfo,PFN_buildInfo);
 RESOLVE(clCreateKernel,PFN_kernel);
 RESOLVE(clSetKernelArg,PFN_arg);
 RESOLVE(clEnqueueNDRangeKernel,PFN_enqueue);
 RESOLVE(clEnqueueReadBuffer,PFN_read);
 RESOLVE(clFinish,PFN_finish);
 RESOLVE(clReleaseMemObject,PFN_release);
 RESOLVE(clReleaseProgram,PFN_release);
 RESOLVE(clReleaseKernel,PFN_release);
 RESOLVE(clReleaseCommandQueue,PFN_release);
 RESOLVE(clReleaseContext,PFN_release);
 cl_context ctx=clCreateContext(nullptr,1,&selected,nullptr,nullptr,&rc);
 if(!ctx||rc){printf("create context failed %d\n",rc);return 7;}
 cl_command_queue queue=clCreateCommandQueue(ctx,selected,0,&rc);
 if(!queue||rc){printf("create queue failed %d\n",rc);return 8;}
 const size_t n=256;
 std::vector<float> aa(n), bb(n),result(n);
 for(size_t i=0;i<n;i++){aa[i]=float(i)*0.25f;bb[i]=float(i)*0.125f;}
 cl_mem a=clCreateBuffer(ctx,1|32,n*sizeof(float),aa.data(),&rc);
 if(rc){printf("buffer a failed %d\n",rc);return 9;}
 cl_mem b=clCreateBuffer(ctx,1|32,n*sizeof(float),bb.data(),&rc);
 if(rc){printf("buffer b failed %d\n",rc);return 10;}
 cl_mem out=clCreateBuffer(ctx,1,n*sizeof(float),nullptr,&rc);
 if(rc){printf("output buffer failed %d\n",rc);return 11;}
 const char* src="__kernel void saxpy(__global const float*a,__global const float*b,__global float*o) {int i=get_global_id(0);o[i]=2.0f*a[i]+b[i];}";
 cl_program prog=clCreateProgramWithSource(ctx,1,&src,nullptr,&rc);
 if(rc){printf("program failed %d\n",rc);return 12;}
 rc=clBuildProgram(prog,1,&selected,nullptr,nullptr,nullptr);
 if(rc){char log[4096]={};clGetProgramBuildInfo(prog,selected,0x1183,sizeof log,log,nullptr);printf("build failed %d: %s\n",rc,log);return 13;}
 cl_kernel k=clCreateKernel(prog,"saxpy",&rc);
 if(rc){printf("kernel failed %d\n",rc);return 14;}
 for(cl_uint i=0;i<3;i++){cl_mem m=i==0?a:i==1?b:out;rc=clSetKernelArg(k,i,sizeof m,&m);if(rc){printf("arg failed %d\n",rc);return 15;}}
 rc=clEnqueueNDRangeKernel(queue,k,1,nullptr,&n,nullptr,0,nullptr,nullptr);
 if(rc){printf("enqueue failed %d\n",rc);return 16;}
 rc=clEnqueueReadBuffer(queue,out,1,0,n*sizeof(float),result.data(),0,nullptr,nullptr);
 if(rc){printf("read failed %d\n",rc);return 17;}
 rc=clFinish(queue);
 if(rc)return 18;
 float maxerr=0;
 for(size_t i=0;i<n;i++){float expected=2.0f*aa[i]+bb[i];maxerr=fmaxf(maxerr,fabsf(result[i]-expected));}
 printf("GPU_KERNEL_COMPLETED n=%zu max_absolute_error=%g\n",n,maxerr);
 clReleaseKernel(k);clReleaseProgram(prog);
 clReleaseMemObject(a);clReleaseMemObject(b);clReleaseMemObject(out);
 clReleaseCommandQueue(queue);clReleaseContext(ctx);
 return maxerr<0.0001?0:19;
}
