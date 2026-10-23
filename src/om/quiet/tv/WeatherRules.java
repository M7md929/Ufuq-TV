package om.quiet.tv;

/** WMO weather codes returned by Open-Meteo. No Android dependencies. */
final class WeatherRules {
    static String icon(int code,boolean day) {
        if(code==0) return day?"sun":"moon";
        if(code==1 || code==2 || code==3) return "cloud";
        if(code==45 || code==48) return "fog";
        if((code>=71 && code<=77) || code==85 || code==86) return "snow";
        if(code>=95) return "storm";
        return "rain";
    }
    static String description(int code,boolean day) {
        if(code==0) return day?"صحو ومشمس":"سماء صافية";
        if(code==1) return "صحو غالبًا";
        if(code==2) return "غائم جزئيًا";
        if(code==3) return "غائم";
        if(code==45 || code==48) return "ضباب";
        if(code>=51 && code<=57) return "رذاذ";
        if(code>=61 && code<=67) return "أمطار";
        if((code>=71 && code<=77) || code==85 || code==86) return "ثلوج";
        if(code>=80 && code<=82) return "زخات مطر";
        if(code>=95 && code<=99) return "عواصف رعدية";
        return "حالة الطقس";
    }
}
