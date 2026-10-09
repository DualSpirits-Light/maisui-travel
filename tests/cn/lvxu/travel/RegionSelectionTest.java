package cn.lvxu.travel;
public final class RegionSelectionTest {
 public static void main(String[] args){int n=0;RegionSelection all=RegionSelection.nationwide();check(all.query().equals("100000"));n++;check(all.label().equals("全国"));n++;check(all.hasCenter());n++;check(all.zoom()==5);n++;
 RegionSelection city=new RegionSelection("甘肃省","兰州市","","620100","city",36.06,103.83);check(city.label().equals("兰州市"));n++;check(city.query().equals("620100"));n++;check(city.zoom()==12);n++;
 RegionSelection district=new RegionSelection(" 甘肃省 ","兰州市","城关区","","district",36.06,103.83);check(district.query().equals("甘肃省兰州市城关区"));n++;check(district.label().equals("城关区"));n++;check(district.zoom()==14);n++;check(!district.sameArea(city));n++;check(!new RegionSelection(null,null,null,null,null,Double.NaN,0).hasCenter());n++;check(RegionSelection.keyword("北京市","北京市","朝阳区").equals("北京市朝阳区"));n++;System.out.println("RegionSelectionTest: "+n+" assertions passed");}
 private static void check(boolean value){if(!value)throw new AssertionError();}
}
