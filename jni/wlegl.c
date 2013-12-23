#include "wlegl_private.h"

#include <stdlib.h>
#include <dlfcn.h>
#include <android/log.h>

#include <hardware/hardware.h>

#include "wayland-android-server-protocol.h"

#define ALOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, "wheatley:wlegl", __VA_ARGS__))
#define ALOGD(...) ((void)__android_log_print(ANDROID_LOG_DEBUG, "wheatley:wlegl", __VA_ARGS__))

static void 
wlegl_create_handle(struct wl_client *client, struct wl_resource *resource,
		    uint32_t id, int32_t num_fds, struct wl_array *ints)
{
	wlegl_handle_create(resource, id, num_fds, ints);
}

static void 
wlegl_create_buffer(struct wl_client *client, struct wl_resource *resource,
		    uint32_t id, int32_t width, int32_t height, int32_t stride,
		    int32_t format, int32_t usage, struct wl_resource *handle)
{
	struct wlegl *wlegl = wl_resource_get_user_data(resource);

	wlegl_buffer_create(wlegl, resource, id, width, height, stride, format,
			    usage, wl_resource_get_user_data(handle));
}

struct android_wlegl_interface wlegl_interface = {
	wlegl_create_handle,
	wlegl_create_buffer
};

static void
wlegl_bind(struct wl_client *client,
	   void *data, uint32_t version, uint32_t id)
{
	struct wlegl *wlegl = data;
	struct wl_resource *resource;

	resource = wl_resource_create(client, &android_wlegl_interface, 1, id);
	if (!resource) {
		wl_client_post_no_memory(client);
		return;
	}

	wl_resource_set_implementation(resource, &wlegl_interface, wlegl, NULL);
}

static void *_libhardware = NULL;
static int (*_hw_get_module)(const char *id, const struct hw_module_t **module) = NULL;

struct wlegl *
wlegl_create(struct wlb_compositor *compositor)
{
	struct wlegl *wlegl;
	const struct hw_module_t *gralloc_module;
	struct wl_display *display;
	int err;

	if (!_libhardware) {
		_libhardware = dlopen("/system/lib/libhardware.so", RTLD_LAZY);
		if (!_libhardware) {
			ALOGE("Failed to open libhardware.so");
			return NULL;
		}
		ALOGD("loaded /system/lib/libhardware.so");

		_hw_get_module = (void *) dlsym(_libhardware, "hw_get_module");
		if (!_hw_get_module) {
			ALOGE("Failed to find hw_get_module");
			return NULL;
		}
	}

	wlegl = malloc(sizeof *wlegl);
	if (!wlegl) {
		ALOGE("Failed to allocate wlegl");
		return NULL;
	}

	err = _hw_get_module(GRALLOC_HARDWARE_MODULE_ID,
			     (const struct hw_module_t **)&wlegl->gralloc);
	if (err) {
		ALOGE("Failed to get gralloc module\n");
		goto err_alloc;
	}

	err = gralloc_open((const struct hw_module_t *)wlegl->gralloc,
			   &wlegl->alloc);
	if (err) {
		ALOGE("Failed to open gralloc: %d\n", strerror(-err));
		goto err_alloc;
	}

	display = wlb_compositor_get_display(compositor);
	wlegl->global = wl_global_create(display, &android_wlegl_interface,
					 1, wlegl, wlegl_bind);
	if (!wlegl->global) {
		ALOGE("Failed to create android_wlegl global\n");
		goto err_gralloc;
	}

	wlb_compositor_add_buffer_type(compositor, &wlegl_buffer_type, wlegl);

	return wlegl;

err_gralloc:
	gralloc_close(wlegl->alloc);
err_alloc:
	free(wlegl);
	return NULL;
}

void
wlegl_destroy(struct wlegl *wlegl)
{
	gralloc_close(wlegl->alloc);
	wl_global_destroy(wlegl->global);
	free(wlegl);
}