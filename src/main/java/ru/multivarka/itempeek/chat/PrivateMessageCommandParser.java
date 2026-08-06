package ru.multivarka.itempeek.chat;

import java.util.*;
import java.util.regex.Pattern;

public final class PrivateMessageCommandParser {
    private static final Pattern ALIAS=Pattern.compile("[a-z0-9_:-]+");
    private PrivateMessageCommandParser(){}
    public static Optional<ParsedPrivateMessage> parse(String input, Collection<String> aliases) {
        if(input==null||!input.startsWith("/"))return Optional.empty(); int i=1; while(i<input.length()&&!Character.isWhitespace(input.charAt(i)))i++;
        String alias=input.substring(1,i).toLowerCase(Locale.ROOT); if(!aliases.contains(alias))return Optional.empty();
        while(i<input.length()&&Character.isWhitespace(input.charAt(i)))i++; int targetStart=i; while(i<input.length()&&!Character.isWhitespace(input.charAt(i)))i++;
        if(targetStart==i)return Optional.empty(); int messageStart=i; while(messageStart<input.length()&&Character.isWhitespace(input.charAt(messageStart)))messageStart++;
        if(messageStart>=input.length())return Optional.empty(); return Optional.of(new ParsedPrivateMessage(alias,input.substring(targetStart,i),input.substring(messageStart),messageStart));
    }
    public static String normalizeAlias(String alias){if(alias==null)return null;String a=alias.trim().toLowerCase(Locale.ROOT);if(a.startsWith("/"))a=a.substring(1);return ALIAS.matcher(a).matches()?a:null;}
}
