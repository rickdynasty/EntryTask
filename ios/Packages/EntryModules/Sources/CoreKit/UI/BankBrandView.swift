#if canImport(UIKit)
import SwiftUI

/// 与工程既有圆形线条标志一致；品牌区供Native认证复用。
public struct BankBrandView: View {
    public init() {}
    public var body: some View {
        HStack(spacing: 10) {
            BankMark().stroke(Color(red: 0.79, green: 0.14, blue: 0.21), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round)).frame(width: 48, height: 48)
            VStack(alignment: .leading) {
                Text("中信银行").font(.system(size: 28, weight: .semibold))
                Text("CHINA CITIC BANK").font(.caption).tracking(1.4)
            }
        }.accessibilityElement(children: .combine)
    }
}
private struct BankMark: Shape {
    func path(in rect: CGRect) -> Path {
        var p = Path(); p.addEllipse(in: CGRect(x: 2, y: 2, width: 20, height: 20))
        for (x, top, bottom) in [(7.0,6.0,18.0),(10,4,20),(14,4,20),(17,6,18)] {
            p.move(to: CGPoint(x: x, y: top)); p.addLine(to: CGPoint(x: x, y: bottom))
        }
        p.move(to: CGPoint(x: 7, y: 12)); p.addLine(to: CGPoint(x: 17, y: 12))
        return p.applying(CGAffineTransform(scaleX: rect.width / 24, y: rect.height / 24))
    }
}
#endif
