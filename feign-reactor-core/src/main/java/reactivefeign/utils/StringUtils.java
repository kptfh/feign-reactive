package reactivefeign.utils;

public class StringUtils {

    public static String cutPrefix(String str, String prefix){
        return str.startsWith(prefix)
                ? str.substring(prefix.length())
                : str;
    }

    public static String cutTail(String str, String tail){
        return str.endsWith(tail)
                ? str.substring(0, str.length() - tail.length())
                : str;
    }
}
