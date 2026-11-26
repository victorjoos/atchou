cmake_minimum_required(VERSION 3.16)

set(EXTRA_COMPONENT_DIRS components)

# Gitignored file holding CONFIG_ATCHOU_DEVICE_TOKEN (and any other local override)
set(SDKCONFIG_DEFAULTS "sdkconfig.defaults")
if(EXISTS "${CMAKE_CURRENT_LIST_DIR}/sdkconfig.secrets")
    list(APPEND SDKCONFIG_DEFAULTS "sdkconfig.secrets")
endif()
include($ENV{IDF_PATH}/tools/cmake/project.cmake)
project(atchou-frame)
