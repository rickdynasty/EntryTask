com.entry.lib.base.mvvm/
│
├── contract/             # 契约层：统一定义 UiState, UiIntent, UiEffect 接口
│   └── BaseContract.kt
│
├── data/                 # 数据层：结合 Room / Retrofit 的统一网络/缓存基类
│   ├── NetworkBoundResource.kt  # 核心：离线缓存与网络同步策略
│   └── Result.kt                # 统一结果密封类
│
├── viewmodel/            # 逻辑层：ViewModel 基类
│   └── BaseViewModel.kt
│
└── ui/                   # 视图层：Compose 声明式基类与高阶包装
    └── ComposableScreen.kt

契约层 (com/entry/lib/base/mvvm/contract/BaseContract.kt)
📌 设计思路、原理与优势
    思路：将传统的离散变量驱动收敛为 UiState（唯一状态树），将一次性事件（Toast、导航）分离为 UiEffect，将用户操作统一抽象为 UiIntent。
    原理：严格遵循 MVI / UDF 单向数据流原则。View 只能发出 Intent，ViewModel 只能修改并输出 UiState。
    优势：极大地减少了多状态不同步导致的 UI Bug，消除了传统 MVVM 多个 LiveData 满天飞的混乱局面。

数据层：统一结果与高性能离线缓存 (com/entry/lib/base/mvvm/data/Result.kt & NetworkBoundResource.kt)
    📌 设计思路、原理与优势
    思路：业务数据大部分来源于 Retrofit（网络） 或 Room（本地数据库）。本模块实现了 Google 官方推荐的 NetworkBoundResource 策略，实现“先读本地缓存渲染，再请求网络同步，最后更新本地并刷新 UI”的高性能离线优先（Offline-First）体验。
    原理：利用 Kotlin Flow 的流式操作符，将本地数据库（Room）的 Flow 与网络请求（Retrofit）无缝结合。
    优势：极大提高 App 的响应速度（秒开），并具备优秀的断网容灾能力。

ViewModel 层 (com/entry/lib/base/mvvm/viewmodel/BaseViewModel.kt)
    📌 设计思路、原理与优势
    思路：封装状态管理的核心逻辑，统一暴露只读的 StateFlow 与 SharedFlow/Channel。
    原理：
        StateFlow：自带去重和缓存最新状
        态能力，保证 UI 重建时能立即恢复当前状态。
        Channel (BUFFERED)：解决一次性副作用（Effect）的背压与安全分发问题。
    优势：将模板代码内聚，业务 ViewModel 只需继承并实现 handleIntent 即可，代码极其干净。

视图层 (com/entry/lib/base/mvvm/ui/ComposableScreen.kt)
    📌 设计思路、原理与优势
    思路：在 Jetpack Compose 架构下，提供标准化的状态收集与副作用生命周期绑定模板。
    原理：使用 collectAsStateWithLifecycle() 挂钩 Android 声明周期，当页面切入后台（STOPPED）时，自动停止流的收集，节省 CPU 算力与内存。
    优势：零样板代码、防止后台刷新带来的内存泄漏。