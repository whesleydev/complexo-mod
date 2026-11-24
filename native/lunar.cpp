// language: C++17, file: lunar.cpp, target: Android arm64 (NDK r26d), jogo Unity IL2CPP
// ponte para libil2cpp.so: leitura de jogadores, projecao de tela e mira.
// roda na thread principal do Android, que e a mesma thread do Unity.

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <math.h>
#include <string.h>
#include <stdio.h>
#include <vector>

#define LOG_TAG "LunarMod"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

struct Vec3 {
    float x, y, z;
};

typedef void* (*fn_domain_get)();
typedef void* (*fn_domain_get_assemblies)(void*, size_t*);
typedef void* (*fn_assembly_get_image)(void*);
typedef const char* (*fn_image_get_name)(void*);
typedef void* (*fn_class_from_name)(void*, const char*, const char*);
typedef void* (*fn_class_get_method_from_name)(void*, const char*, int);
typedef void* (*fn_class_get_field_from_name)(void*, const char*);
typedef size_t (*fn_field_get_offset)(void*);
typedef void* (*fn_runtime_invoke)(void*, void*, void**, void**);
typedef void* (*fn_class_get_type)(void*);
typedef void* (*fn_value_box)(void*, void*);
typedef void* (*fn_object_unbox)(void*);
typedef size_t (*fn_array_length)(void*);

struct Api {
    fn_domain_get domain_get = nullptr;
    fn_domain_get_assemblies domain_get_assemblies = nullptr;
    fn_assembly_get_image assembly_get_image = nullptr;
    fn_image_get_name image_get_name = nullptr;
    fn_class_from_name class_from_name = nullptr;
    fn_class_get_method_from_name method_from_name = nullptr;
    fn_class_get_field_from_name field_from_name = nullptr;
    fn_field_get_offset field_offset = nullptr;
    fn_runtime_invoke runtime_invoke = nullptr;
    fn_class_get_type class_get_type = nullptr;
    fn_value_box value_box = nullptr;
    fn_object_unbox object_unbox = nullptr;
    fn_array_length array_length = nullptr;
};

struct Target {
    void* player_class = nullptr;
    void* component_class = nullptr;
    void* transform_class = nullptr;
    void* camera_class = nullptr;
    void* vector3_class = nullptr;
    void* object_class = nullptr;

    void* get_transform = nullptr;
    void* get_position = nullptr;
    void* world_to_screen = nullptr;
    void* camera_main = nullptr;
    void* look_at = nullptr;
    void* get_instance = nullptr;
    void* find_objects = nullptr;
    void* find_objects_all = nullptr;

    size_t off_hp = 0;
    size_t off_alive = 0;
    size_t off_settings = 0;
    bool ready = false;
};

Api g_api;
Target g_target;

float g_esp_scale = 1.0f;
int g_last_error = 0;

void* resolve(const char* name) {
    void* symbol = dlsym(RTLD_DEFAULT, name);
    if (!symbol) {
        void* handle = dlopen("libil2cpp.so", RTLD_NOW | RTLD_NOLOAD);
        if (handle) {
            symbol = dlsym(handle, name);
        }
    }
    return symbol;
}

template <typename T>
T bind(const char* name) {
    return reinterpret_cast<T>(resolve(name));
}

void* find_image(const char* wanted) {
    size_t count = 0;
    void* domain = g_api.domain_get();
    if (!domain) {
        return nullptr;
    }
    void** assemblies = reinterpret_cast<void**>(g_api.domain_get_assemblies(domain, &count));
    if (!assemblies) {
        return nullptr;
    }
    for (size_t index = 0; index < count; ++index) {
        void* image = g_api.assembly_get_image(assemblies[index]);
        const char* name = image ? g_api.image_get_name(image) : nullptr;
        if (name && strcmp(name, wanted) == 0) {
            return image;
        }
    }
    return nullptr;
}

bool init_api() {
    g_api.domain_get = bind<fn_domain_get>("il2cpp_domain_get");
    g_api.domain_get_assemblies = bind<fn_domain_get_assemblies>("il2cpp_domain_get_assemblies");
    g_api.assembly_get_image = bind<fn_assembly_get_image>("il2cpp_assembly_get_image");
    g_api.image_get_name = bind<fn_image_get_name>("il2cpp_image_get_name");
    g_api.class_from_name = bind<fn_class_from_name>("il2cpp_class_from_name");
    g_api.method_from_name = bind<fn_class_get_method_from_name>("il2cpp_class_get_method_from_name");
    g_api.field_from_name = bind<fn_class_get_field_from_name>("il2cpp_class_get_field_from_name");
    g_api.field_offset = bind<fn_field_get_offset>("il2cpp_field_get_offset");
    g_api.runtime_invoke = bind<fn_runtime_invoke>("il2cpp_runtime_invoke");
    g_api.class_get_type = bind<fn_class_get_type>("il2cpp_class_get_type");
    g_api.value_box = bind<fn_value_box>("il2cpp_value_box");
    g_api.object_unbox = bind<fn_object_unbox>("il2cpp_object_unbox");
    g_api.array_length = bind<fn_array_length>("il2cpp_array_length");

    bool ok = g_api.domain_get && g_api.domain_get_assemblies && g_api.assembly_get_image
            && g_api.image_get_name && g_api.class_from_name && g_api.method_from_name
            && g_api.field_from_name && g_api.field_offset && g_api.runtime_invoke
            && g_api.class_get_type && g_api.value_box && g_api.object_unbox
            && g_api.array_length;
    if (!ok) {
        LOGE("simbolos il2cpp nao resolvidos");
    }
    return ok;
}

size_t field_offset(void* klass, const char* name) {
    void* field = g_api.field_from_name(klass, name);
    return field ? g_api.field_offset(field) : 0;
}

bool init_targets() {
    void* game = find_image("Assembly-CSharp.dll");
    void* core = find_image("UnityEngine.CoreModule.dll");
    if (!game || !core) {
        LOGE("imagens nao encontradas (game=%p core=%p)", game, core);
        return false;
    }

    g_target.player_class = g_api.class_from_name(game, "", "PlayerBehaviour");
    g_target.object_class = g_api.class_from_name(core, "UnityEngine", "Object");
    g_target.component_class = g_api.class_from_name(core, "UnityEngine", "Component");
    g_target.transform_class = g_api.class_from_name(core, "UnityEngine", "Transform");
    g_target.camera_class = g_api.class_from_name(core, "UnityEngine", "Camera");
    g_target.vector3_class = g_api.class_from_name(core, "UnityEngine", "Vector3");

    if (!g_target.player_class || !g_target.component_class || !g_target.transform_class
            || !g_target.camera_class || !g_target.vector3_class) {
        LOGE("classes base nao encontradas");
        return false;
    }

    g_target.get_transform = g_api.method_from_name(g_target.component_class, "get_transform", 0);
    g_target.get_position = g_api.method_from_name(g_target.transform_class, "get_position", 0);
    g_target.world_to_screen = g_api.method_from_name(g_target.camera_class, "WorldToScreenPoint", 1);
    g_target.camera_main = g_api.method_from_name(g_target.camera_class, "get_main", 0);
    g_target.look_at = g_api.method_from_name(g_target.transform_class, "LookAt", 1);
    g_target.get_instance = g_api.method_from_name(g_target.player_class, "GetInstance", 0);
    if (g_target.object_class) {
        g_target.find_objects = g_api.method_from_name(g_target.object_class, "FindObjectsOfType", 1);
    }

    g_target.off_hp = field_offset(g_target.player_class, "hp");
    g_target.off_alive = field_offset(g_target.player_class, "IsAlive");
    g_target.off_settings = field_offset(g_target.player_class, "movementSettings");

    g_target.ready = g_target.get_transform && g_target.get_position && g_target.world_to_screen
            && g_target.camera_main && g_target.get_instance;
    if (!g_target.ready) {
        LOGE("metodos essenciais ausentes (transform=%p pos=%p w2s=%p cam=%p inst=%p)",
             g_target.get_transform, g_target.get_position, g_target.world_to_screen,
             g_target.camera_main, g_target.get_instance);
    } else {
        LOGI("alvos prontos: hp@0x%zx alive@0x%zx", g_target.off_hp, g_target.off_alive);
    }
    return g_target.ready;
}

void* call(void* method, void* instance, void** params) {
    void* exception = nullptr;
    void* result = g_api.runtime_invoke(method, instance, params, &exception);
    if (exception) {
        g_last_error = 1;
        return nullptr;
    }
    return result;
}

bool world_position(void* component, Vec3& out) {
    if (!component) {
        return false;
    }
    void* transform = call(g_target.get_transform, component, nullptr);
    if (!transform) {
        return false;
    }
    void* boxed = call(g_target.get_position, transform, nullptr);
    if (!boxed) {
        return false;
    }
    out = *reinterpret_cast<Vec3*>(g_api.object_unbox(boxed));
    return true;
}

bool screen_point(void* camera, const Vec3& world, Vec3& out) {
    Vec3 copy = world;
    void* argument = g_api.value_box(g_target.vector3_class, &copy);
    void* params[1] = {argument};
    void* boxed = call(g_target.world_to_screen, camera, params);
    if (!boxed) {
        return false;
    }
    out = *reinterpret_cast<Vec3*>(g_api.object_unbox(boxed));
    return out.z > 0.1f;
}

std::vector<void*> collect_players() {
    std::vector<void*> players;
    if (!g_target.find_objects || !g_target.object_class) {
        return players;
    }
    void* type = g_api.class_get_type(g_target.player_class);
    if (!type) {
        return players;
    }
    void* params[1] = {type};
    void* array = call(g_target.find_objects, nullptr, params);
    if (!array) {
        return players;
    }
    size_t count = g_api.array_length(array);
    if (count > 64) {
        count = 64;
    }
    void** items = reinterpret_cast<void**>(reinterpret_cast<char*>(array) + sizeof(void*));
    for (size_t index = 0; index < count; ++index) {
        if (items[index]) {
            players.push_back(items[index]);
        }
    }
    return players;
}

void* local_player() {
    return call(g_target.get_instance, nullptr, nullptr);
}

float distance_between(const Vec3& a, const Vec3& b) {
    float dx = a.x - b.x;
    float dy = a.y - b.y;
    float dz = a.z - b.z;
    return sqrtf(dx * dx + dy * dy + dz * dz);
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_lunar_mod_Engine_nativeInit(JNIEnv*, jclass) {
    if (g_target.ready) {
        return JNI_TRUE;
    }
    if (!init_api()) {
        return JNI_FALSE;
    }
    return init_targets() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_lunar_mod_Engine_nativeTick(JNIEnv* env, jclass, jboolean esp, jboolean aim, jfloat fov) {
    if (!g_target.ready && !init_targets()) {
        return nullptr;
    }

    void* me = local_player();
    Vec3 my_position{};
    bool have_me = world_position(me, my_position);
    void* camera = call(g_target.camera_main, nullptr, nullptr);
    if (!camera) {
        return nullptr;
    }

    std::vector<void*> players = collect_players();
    std::vector<float> output;
    output.push_back(0.0f);

    void* best_target = nullptr;
    Vec3 best_position{};
    float best_distance = 1e9f;
    float best_screen_distance = 1e9f;

    for (void* player : players) {
        if (player == me) {
            continue;
        }
        Vec3 position{};
        if (!world_position(player, position)) {
            continue;
        }

        float health = g_target.off_hp ? *reinterpret_cast<float*>(
                reinterpret_cast<char*>(player) + g_target.off_hp) : 0.0f;
        float distance = have_me ? distance_between(my_position, position) : 0.0f;

        Vec3 head = position;
        head.y += 1.55f;
        Vec3 screen{};
        bool visible = screen_point(camera, head, screen);
        if (visible && esp) {
            float width_px = 0.0f;
            if (have_me && distance > 0.01f) {
                width_px = (900.0f / distance) * g_esp_scale;
            }
            output.push_back(screen.x);
            output.push_back(screen.y);
            output.push_back(distance);
            output.push_back(health);
            output.push_back(width_px);
            float center_x = screen.x - 960.0f;
            float center_y = screen.y - 540.0f;
            float offset = sqrtf(center_x * center_x + center_y * center_y);
            if (offset < best_screen_distance && offset < fov) {
                best_screen_distance = offset;
                best_target = player;
                best_position = head;
            } else if (offset < best_screen_distance) {
                best_screen_distance = offset;
                best_target = player;
                best_position = head;
            }
        }

    }

    if (aim && best_target) {
        void* camera_transform = call(g_target.get_transform, camera, nullptr);
        if (camera_transform && g_target.look_at) {
            Vec3 aim_position = best_position;
            void* argument = g_api.value_box(g_target.vector3_class, &aim_position);
            void* params[1] = {argument};
            call(g_target.look_at, camera_transform, params);
        }
    }

    output[0] = static_cast<float>((output.size() - 1) / 5);
    jfloatArray result = env->NewFloatArray(static_cast<jsize>(output.size()));
    if (!result) {
        return nullptr;
    }
    env->SetFloatArrayRegion(result, 0, static_cast<jsize>(output.size()), output.data());
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_lunar_mod_Engine_nativeStatus(JNIEnv* env, jclass) {
    char buffer[256];
    snprintf(buffer, sizeof(buffer),
             "ready=%d hp@0x%zx chamadas_erro=%d imagem=%s",
             g_target.ready ? 1 : 0, g_target.off_hp, g_last_error,
             g_target.ready ? "Assembly-CSharp" : "nao resolvida");
    return env->NewStringUTF(buffer);
}

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM*, void*) {
    LOGI("liblunar carregada");
    return JNI_VERSION_1_6;
}
