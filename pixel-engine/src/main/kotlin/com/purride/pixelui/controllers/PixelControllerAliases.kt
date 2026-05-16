package com.purride.pixelui

import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelTextFieldController

/**
 * Flutter 风格控制器公开别名。
 *
 * 当前阶段先统一对外命名，
 * 内部仍然复用已有 `Pixel*Controller` 实现，避免拆出第二套状态语义。
 */
typealias PageController = PixelPagerController
typealias ScrollController = PixelListController
typealias TextEditingController = PixelTextFieldController
