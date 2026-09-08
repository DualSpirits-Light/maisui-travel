package cn.lvxu.travel;

/** Coordinate conversion for display; mainland GCJ02 inverse is approximate. */
final class GeoMath {
    static double[] wgs(double lat, double lon, String system) {
        if ("BD09".equals(system)) {
            double x=lon-.0065,y=lat-.006;
            double z=Math.sqrt(x*x+y*y)-.00002*Math.sin(y*Math.PI*3000/180);
            double theta=Math.atan2(y,x)-.000003*Math.cos(x*Math.PI*3000/180);
            return wgs(z*Math.sin(theta),z*Math.cos(theta),"GCJ02");
        }
        if (!"GCJ02".equals(system) || lon < 72.004 || lon > 137.8347 || lat < .8293 || lat > 55.8271)
            return new double[]{lat, lon};
        double guessLat = lat, guessLon = lon;
        for (int i = 0; i < 4; i++) {
            double[] shifted = gcj(guessLat, guessLon);
            guessLat -= shifted[0] - lat;
            guessLon -= shifted[1] - lon;
        }
        return new double[]{guessLat, guessLon};
    }
    static double[] gcj(double lat, double lon) {
        double x = lon - 105, y = lat - 35;
        double dLat = -100 + 2*x + 3*y + .2*y*y + .1*x*y + .2*Math.sqrt(Math.abs(x));
        dLat += (20*Math.sin(6*x*Math.PI) + 20*Math.sin(2*x*Math.PI))*2/3;
        dLat += (20*Math.sin(y*Math.PI) + 40*Math.sin(y/3*Math.PI))*2/3;
        dLat += (160*Math.sin(y/12*Math.PI) + 320*Math.sin(y*Math.PI/30))*2/3;
        double dLon = 300 + x + 2*y + .1*x*x + .1*x*y + .1*Math.sqrt(Math.abs(x));
        dLon += (20*Math.sin(6*x*Math.PI) + 20*Math.sin(2*x*Math.PI))*2/3;
        dLon += (20*Math.sin(x*Math.PI) + 40*Math.sin(x/3*Math.PI))*2/3;
        dLon += (150*Math.sin(x/12*Math.PI) + 300*Math.sin(x/30*Math.PI))*2/3;
        double rad = lat/180*Math.PI, magic = 1-.00669342162296594323*Math.sin(rad)*Math.sin(rad);
        double sqrt = Math.sqrt(magic);
        dLat = dLat*180/((6378245*(1-.00669342162296594323))/(magic*sqrt)*Math.PI);
        dLon = dLon*180/(6378245/sqrt*Math.cos(rad)*Math.PI);
        return new double[]{lat+dLat, lon+dLon};
    }
    static double meters(double lat1, double lon1, double lat2, double lon2) {
        double a = Math.toRadians(lat2-lat1), b = Math.toRadians(lon2-lon1);
        double h = Math.sin(a/2)*Math.sin(a/2) + Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.sin(b/2)*Math.sin(b/2);
        return 6371008.8 * 2 * Math.atan2(Math.sqrt(Math.max(0,h)), Math.sqrt(Math.max(0,1-h)));
    }
    static String distance(double meters) {
        return meters >= 1000 ? String.format(java.util.Locale.ROOT,"%.2f km",meters/1000) : String.format(java.util.Locale.ROOT,"%.0f m",meters);
    }
}
