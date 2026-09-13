package cn.lvxu.travel;

/** Regression coverage for first launch versus the complete release history. */
public final class ReleaseNotesTest {
    public static void main(String[] args){
        String current=ReleaseNotes.currentChanges("0.4.0"),history=ReleaseNotes.history();
        if(!current.contains("探索 AI")||current.contains("0.3.1"))throw new AssertionError("first dialog is current version only");
        for(String marker:new String[]{"0.4.0","0.3.1","0.2.5","图片旅行分享","在线激活"})if(!history.contains(marker))throw new AssertionError("history missing "+marker);
        if(!"本版本包含稳定性改进。".equals(ReleaseNotes.currentChanges("9.9.9")))throw new AssertionError("unknown version fallback");
        System.out.println("PASS: release notes assertions");
    }
}
