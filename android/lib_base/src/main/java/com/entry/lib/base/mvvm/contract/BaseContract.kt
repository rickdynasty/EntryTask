package com.entry.lib.base.mvvm.contract

/**
 * UI 状态标记接口：代表页面的唯一状态快照（Single Source of Truth）
 * 统一定义架构的三大支柱（State 状态、Intent 意图、Effect 事件）
 * 确立不可变数据契约，让 UI 的每一帧都具备可预测性
 */
interface IUiState

/**
 * UI 一次性副作用标记接口：用于处理 Toast、弹窗、页面跳转等不应随重组丢失或重复消费的事件
 */
interface IUiEffect

/**
 * UI 动作/意图标记接口：用户在 View 层触发的所有行为动作
 */
interface IUiIntent