package nl.opengeogroep.safetymaps.server.cache;

import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

public class AuthIncLocCacheItem {
  private Integer id;
  private String loc;

  public String GetIdString() {
    return this.id.toString();
  }

  public AuthIncLocCacheItem(Integer id, String loc) {
    this.id = id;
    this.loc = loc;
  }

  public Boolean PointIsInLoc(Point p) throws ParseException {
    GeometryFactory gf = new GeometryFactory();
    WKTReader wr = new WKTReader(gf);
    Polygon poly = (Polygon) wr.read(this.loc);

    return poly.contains(p);
  }
}
