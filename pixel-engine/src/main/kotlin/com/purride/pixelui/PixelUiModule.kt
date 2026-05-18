package com.purride.pixelui

/**
 * 像素 UI runtime 分层占位入口。
 *
 * 这里将承接通用组件树、布局、命中测试、输入分发和滚动分页等能力，
 * 保持只依赖 pixel engine 的 core package，不直接依赖 Launcher 业务代码。
 */
public object PixelUiModule
