# 原型装饰素材

PNG 来自旧版原型（现存放于`prd/UI原型/*.jpg`） 的局部装饰图：银杏山水、礼盒、会员卡、皇冠、书籍、客服头像、游客头像和首页四个金色快捷图标。原始图片由用户提供，仅用于本工程原型视觉还原。未使用整页截图作为 UI；所有业务文字、布局和交互控件由RN或Native控件实现。

源文件是有损压缩截图，局部装饰图的背景/清晰度有相应限制；正式交付设计素材后按同名 asset key 替换。credit-gift 图内含不可分离的装饰营销字样，不作为动态业务文案或承诺。线性图标和业绩演示图使用 SVG 原语。

Android的biz_dashboard通过AGP生成任务从本目录打包PNG到build生成资源目录，源素材不复制到平台源码。四根页Native模式详见[实施说明](../../../docs/development/native-dashboard.md)。

`icons.json`为RN与Android共用的完整线性图标路径。RN通过Icon绘制，Android的DashboardArtTask生成vector；不要在RN额外叠加圆圈/装饰线，否则会与Native产生差异。品牌线稿沿用现有工程形状，正式Logo/VIP纹理的素材限制见[新版实施记录](../../../docs/development/新版四Tab实施记录.md)。
