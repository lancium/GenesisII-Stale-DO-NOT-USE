#include <stdio.h>
#include <string.h>
#include "StringManipulation.h"
#include "Utility.h"
#include "MakeGenesisIICalls.h"

/* Static variables for library to keep track of JVM */
static int DEBUG = 1;

/* The java VM for this process */
JavaVM *jvm=NULL;

/* Helper function */
int get_static_method(PGII_JNI_INFO info, jclass *my_class, char* method, char* method_type, jmethodID *mid);

DllExport int genesisII_directory_listing(PGII_JNI_INFO info, char *** listing, 
		char * directory, char * target){		
	jmethodID mid;
	
	if(get_static_method(info,&(info->jni_launcher), "getDirectoryListing", "(Ljava/lang/String;Ljava/lang/String;)[Ljava/lang/Object;", &mid) != JNI_ERR)
	{
		/* Build arguments */
		jstring j_arg1 = directory == NULL ? NULL : NewPlatformString(info->env, directory, -1);
		jstring j_arg2 = target == NULL ? NULL : NewPlatformString(info->env, target, -1);		

		/* Invoke Method */
		jarray jlisting = (*info->env)->CallStaticObjectMethod(info->env, info->jni_launcher, mid, 
			j_arg1, j_arg2);						

		/* Convert to char** and return */
		return convert_listing(info->env, listing, jlisting);
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;
}

DllExport int genesisII_get_information(PGII_JNI_INFO info, char *** gInfo, char * path){		
	jmethodID mid;

	if(get_static_method(info,&(info->jni_launcher), "getInformation", "(Ljava/lang/String;)[Ljava/lang/Object;", &mid) != JNI_ERR)
	{
		/* Build arguments */
		jstring j_arg1 = path == NULL ? NULL : NewPlatformString(info->env, path, -1);		

		/* Invoke Method */
		jarray jlisting = (*info->env)->CallStaticObjectMethod(info->env, info->jni_launcher, mid, 
			j_arg1);						

		/* Convert to char** and return */
		return convert_listing(info->env, gInfo, jlisting);
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;
}

DllExport int genesisII_change_directory(PGII_JNI_INFO info, char* new_directory){	
	jmethodID mid;

	if(new_directory == NULL){
		return JNI_ERR;
	}

	if(get_static_method(info,&(info->jni_launcher), "changeDirectory", "(Ljava/lang/String;)Z", &mid) != JNI_ERR)
	{
		/* Build argument */
		jstring j_argument = NewPlatformString(info->env, new_directory, -1);			

		 /* Invoke method. */			
		jboolean if_success = (*info->env)->CallStaticBooleanMethod(info->env, info->jni_launcher, mid, j_argument);
		return ( if_success -1 );
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;				
}

DllExport char* genesisII_get_working_directory(PGII_JNI_INFO info){
	jmethodID mid;

	if(get_static_method(info,&(info->jni_launcher), "getCurrentDirectory", "()Ljava/lang/String;", &mid) != JNI_ERR)
	{		
		jstring jworking_directory = (*info->env)->CallStaticObjectMethod(info->env, info->jni_launcher, mid);
		return convert_jstring(info->env, jworking_directory);
		
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return NULL;	
}

DllExport int genesisII_login(PGII_JNI_INFO info, char * keystore_path, 
		char * password, char * cert_pattern){
	jmethodID mid;

	if(keystore_path == NULL || password == NULL){
		return JNI_ERR;
	}

	if(get_static_method(info,&(info->jni_launcher), "login", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z", &mid) != JNI_ERR)
	{
		/* Build arguments */
		jstring j_arg1 = NewPlatformString(info->env, keystore_path, -1);
		jstring j_arg2 = NewPlatformString(info->env, password, -1);		
		jstring j_arg3 = NewPlatformString(info->env, cert_pattern, -1);		

		/* Invoke Method */
		jboolean if_success = (*info->env)->CallStaticBooleanMethod(info->env, info->jni_launcher, mid, j_arg1, j_arg2, j_arg3);					

		/* Convert to char** and return */
		return (if_success - 1);
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;
}
DllExport int genesisII_logout(PGII_JNI_INFO info){
	jmethodID mid;

	if(get_static_method(info,&(info->jni_launcher), "logout", "()V", &mid) != JNI_ERR)
	{
		/* Invoke Method */
		(*info->env)->CallStaticVoidMethod(info->env, info->jni_launcher, mid);		
		return JNI_OK;
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;	
}

DllExport int genesisII_make_directory(PGII_JNI_INFO info, char * new_directory){
	jmethodID mid;

	if(new_directory == NULL){
		return JNI_ERR;
	}

	if(get_static_method(info,&(info->jni_launcher), "makeDirectory", "(Ljava/lang/String;)Z", &mid) != JNI_ERR)
	{		
		/* Build argument */
		jstring j_argument = NewPlatformString(info->env, new_directory, -1);		

		jboolean if_success = (*info->env)->CallStaticBooleanMethod(info->env, info->jni_launcher, mid, j_argument);
		return (if_success - 1);		
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;
}

DllExport int genesisII_remove(PGII_JNI_INFO info, char * path, int recursive, int force){
	jmethodID mid;

	if((recursive != JNI_TRUE && recursive != JNI_FALSE) || 
		force != JNI_TRUE && force != JNI_FALSE){
			return JNI_ERR;
	}

	if(get_static_method(info,&(info->jni_launcher), "remove", "(Ljava/lang/String;ZZ)Z", &mid) != JNI_ERR)
	{		
		/* Build argument */
		jstring j_argument = NewPlatformString(info->env, path, -1);		

		jboolean if_success = (*info->env)->CallStaticBooleanMethod(info->env, info->jni_launcher, mid, j_argument,
			recursive, force);
		return (if_success -1 );		
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;
}

DllExport int genesisII_open(PGII_JNI_INFO info, char * target, 
		int create, int read, int write, char *** returnArray){
	jmethodID mid;

	if(get_static_method(info,&(info->jni_launcher), "open", "(Ljava/lang/String;ZZZ)[Ljava/lang/Object;", &mid) != JNI_ERR)
	{		
		/* Build argument */
		jstring j_argument = NewPlatformString(info->env, target, -1);		

		/* Invoke Method */
		jarray jlisting = (*info->env)->CallStaticObjectMethod(info->env, info->jni_launcher, mid, j_argument, create, 
			read, write);								

		/* Convert to char** and return */
		return convert_listing(info->env, returnArray, jlisting);
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;
}

DllExport int genesisII_read(PGII_JNI_INFO info, GII_FILE_HANDLE target, char* data, 
		int offset, int length){
	jmethodID mid;

	if(get_static_method(info,&(info->jni_launcher), "read", "(III)Ljava/lang/String;", &mid) != JNI_ERR)
	{			
		jstring my_read = (*info->env)->CallStaticObjectMethod(info->env, info->jni_launcher, mid, target, offset, length);
		return convert_jstring_using_data(info->env, my_read, data);
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;
}

DllExport int genesisII_write(PGII_JNI_INFO info, GII_FILE_HANDLE target, 
		char* data, int offset, int length){
	jmethodID mid;
	int bytes_written;

	if(get_static_method(info,&(info->jni_launcher), "write", "(ILjava/lang/String;I)I", &mid) != JNI_ERR)
	{		
		jstring j_data = NewPlatformString(info->env, data, length);		

		bytes_written = (*info->env)->CallStaticIntMethod(info->env, info->jni_launcher, mid, target, j_data, offset);
		return bytes_written;
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;
}

DllExport int genesisII_close(PGII_JNI_INFO info, GII_FILE_HANDLE handle){
	jmethodID mid;
	int return_val;

	if(get_static_method(info,&(info->jni_launcher), "close", "(I)Z", &mid) != JNI_ERR)
	{				
		return_val = (*info->env)->CallStaticBooleanMethod(info->env, info->jni_launcher, mid, handle);
		return (return_val - 1);
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;
}

DllExport int genesisII_copy(PGII_JNI_INFO info, char * src, char * dst, 
		int src_local, int dst_local){
	jmethodID mid;
	if((src_local != JNI_TRUE && src_local != JNI_FALSE) || 
		dst_local != JNI_TRUE && dst_local != JNI_FALSE){
			return JNI_ERR;
	}
	if(src == NULL || dst == NULL){
		return JNI_ERR;
	}

	if(get_static_method(info,&(info->jni_launcher), "copy", "(Ljava/lang/String;Ljava/lang/String;ZZ)Z", &mid) != JNI_ERR)
	{		
		/* Build arguments */
		jstring j_arg1 = NewPlatformString(info->env, src, -1);		
		jstring j_arg2 = NewPlatformString(info->env, dst, -1);		

		return ((*info->env)->CallStaticBooleanMethod(info->env, info->jni_launcher, mid, j_arg1, j_arg2, src_local, dst_local) - 1);
	}
	else{
		printf("GenesisII Error:  Could not find the method specified for this call\n");
	}	
	return JNI_ERR;

}

DllExport int genesisII_move(PGII_JNI_INFO info, char * src, char * dst){
	if(src == NULL || dst == NULL){
		return JNI_ERR;
	}
	if(genesisII_copy(info, src, dst, JNI_FALSE, JNI_FALSE) != JNI_ERR){
		return genesisII_remove(info, src, JNI_FALSE, JNI_TRUE);
	}
	else{
		return JNI_ERR;
	}
}

/* Extra initialization and helper functions */
DllExport int initializeJavaVM(char * genesis_directory, PGII_JNI_INFO newInfo){
	JavaVMOption options[4];	
	JavaVMInitArgs vm_args;
	long status;	
	char op0[512],op1[255],op2[255],op3[255];

	/* default directory */
	if(genesis_directory == NULL){
		genesis_directory = "'C:/Program Files/Genesis II'";
	}

	sprintf_s(op0, 512, "-Djava.class.path=%s/ext/bouncycastle/bcprov-jdk15-133.jar;%s/lib/GenesisII-security.jar;%s/lib/morgan-utilities.jar;%s/lib;%s/security;", genesis_directory, genesis_directory, genesis_directory, genesis_directory, genesis_directory);	
	sprintf_s(op1, 255, "-Dlog4j.configuration=genesisII.log4j.properties");
	sprintf_s(op2, 255, "-Djava.library.path=%s/jni-lib", genesis_directory);
	sprintf_s(op3, 255, "-Dedu.virginia.vcgr.genii.install-base-dir=%s", genesis_directory);	

	options[0].optionString = op0;
	options[1].optionString = op1;
	options[2].optionString = op2;
	options[3].optionString = op3;
	//options[4].optionString = "-Xms256M";
	//options[5].optionString = "-Xmx512M";

	memset(&vm_args, 0, sizeof(vm_args));
	vm_args.version = JNI_VERSION_1_2;
	vm_args.nOptions = 4;
	vm_args.options = options;	

	if(DEBUG == 1)
	{
		printf("Initializing JVM with %s directory ... ", genesis_directory);
	}

	status = JNI_CreateJavaVM(&jvm, (void**)&newInfo->env, &vm_args);	
	newInfo->jni_launcher = ((*newInfo->env)->FindClass(newInfo->env, "org/morgan/util/launcher/JNILibraryLauncher"));

	if(newInfo->jni_launcher == 0){
		printf("GenesisII Error: Missing JNILibraryLauncher class");
		status = JNI_ERR;
	}
	else if(DEBUG == 1)
	{
		printf("done.\n");
	}

	return status;
}

DllExport int initializeJavaVMForThread(PGII_JNI_INFO newInfo){
	int res;

	if(jvm == NULL){
		return JNI_ERR;
	}
	 
    res = (*jvm)->AttachCurrentThread(jvm, (void**)&(newInfo->env), NULL);
	if (res < 0) {
		printf("Attach failed\n");
		return JNI_ERR;
	}
	
	newInfo->jni_launcher = ((*newInfo->env)->FindClass(newInfo->env, "org/morgan/util/launcher/JNILibraryLauncher"));

	if (newInfo->jni_launcher == NULL) {
		(*jvm)->DetachCurrentThread(jvm);
		return JNI_ERR;
	}

	return res;
}

int get_static_method(PGII_JNI_INFO info, jclass *my_class, char* method, char* method_type, jmethodID *mid){	

	if(*my_class == 0){
		if(initializeJavaVM(NULL, info) == JNI_ERR){
			return JNI_ERR;
		}
	}
	*mid = (*info->env)->GetStaticMethodID(info->env, *my_class, method, method_type);
	if(*mid != 0)
	{
		return JNI_OK;
	}

	return JNI_ERR;
}