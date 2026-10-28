import Foundation

/// 对齐Kotlin Char.isWhitespace：不误把NEL、零宽字符或BOM当可裁剪空白。
public enum InputText {
    private static func whitespace(_ scalar: Unicode.Scalar) -> Bool {
        switch scalar.value {
        case 0x09...0x0D, 0x1C...0x20, 0xA0, 0x1680, 0x2000...0x200A, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000: return true
        default: return false
        }
    }
    public static func trim(_ text: String) -> String {
        let scalars = Array(text.unicodeScalars)
        var start = 0; var end = scalars.count
        while start < end && whitespace(scalars[start]) { start += 1 }
        while end > start && whitespace(scalars[end - 1]) { end -= 1 }
        return String(String.UnicodeScalarView(scalars[start..<end]))
    }
}
