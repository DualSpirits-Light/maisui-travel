package cn.lvxu.travel;

import java.util.ArrayList;
import java.util.List;

/** Validation and photo selection for an unsaved check-in draft. */
final class CheckinEditRules {
    static ArrayList<String> companions(String input) {
        ArrayList<String> people = new ArrayList<>();
        for (String person : input.split("[,，;；、\\r\\n]+")) {
            person = person.trim();
            if (person.length() > 40) throw new IllegalArgumentException("每位同行人员最多 40 字");
            if (!person.isEmpty() && !people.contains(person)) people.add(person);
        }
        if (people.size() > 50) throw new IllegalArgumentException("同行人员最多 50 位");
        return people;
    }
    static ArrayList<String> editableGroupPhotos(List<String> group, List<String> scenery, String legacy) {
        ArrayList<String> photos = new ArrayList<>(group);
        if (photos.isEmpty() && scenery.isEmpty() && legacy != null && !legacy.isEmpty()) photos.add(legacy);
        return photos;
    }
    static String cover(List<String> group, List<String> scenery, String legacy) {
        if (!scenery.isEmpty()) return scenery.get(0);
        if (!group.isEmpty()) return group.get(0);
        return "";
    }
}
