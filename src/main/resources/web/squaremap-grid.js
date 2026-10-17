/*
 * MRchunks - Chunkraster für squaremap.
 * Wird von MRchunks in den Web-Ordner von squaremap kopiert und VOR squaremaps eigenem Skript eingebunden.
 * squaremap legt seine Leaflet-Karte nicht global ab; deshalb fangen wir die Zuweisung von window.L ab und
 * registrieren einen Init-Hook, bevor squaremap die Karte erzeugt.
 */
(function () {
  "use strict";
  var CFG = __MRCHUNKS_CONFIG__;
  var STORAGE_KEY = "mrchunks-grid-visible";
  var leaflet;

  Object.defineProperty(window, "L", {
    configurable: true,
    get: function () { return leaflet; },
    set: function (value) {
      leaflet = value;
      try {
        value.Map.addInitHook(function () { setup(this); });
      } catch (e) {
        console.warn("MRchunks: Chunkraster konnte nicht eingebunden werden", e);
      }
    }
  });

  function remembered() {
    try {
      var v = window.localStorage.getItem(STORAGE_KEY);
      return v === null ? CFG.defaultVisible : v === "1";
    } catch (e) {
      return CFG.defaultVisible;
    }
  }

  function remember(visible) {
    try { window.localStorage.setItem(STORAGE_KEY, visible ? "1" : "0"); } catch (e) { /* private mode */ }
  }

  /** maxNativeZoom of squaremap's tile layer = zoom level where 1 pixel is 1 block. */
  function nativeMaxZoom(map) {
    var found = null;
    map.eachLayer(function (layer) {
      if (layer instanceof leaflet.TileLayer && layer.options && layer.options.maxNativeZoom != null) {
        found = layer.options.maxNativeZoom;
      }
    });
    return found;
  }

  function setup(map) {
    var pane = map.createPane("mrchunksGrid");
    pane.style.zIndex = 350;          // above the map tiles, below squaremap's markers
    pane.style.pointerEvents = "none";

    var Grid = leaflet.GridLayer.extend({
      createTile: function (coords) {
        var size = this.getTileSize();
        var canvas = document.createElement("canvas");
        canvas.width = size.x;
        canvas.height = size.y;
        var max = nativeMaxZoom(map);
        if (max === null) {
          return canvas;
        }
        // Blocks per screen pixel at this zoom (CRS.Simple: pixel = block * 2^(zoom - maxNativeZoom)).
        var bpp = Math.pow(2, max - coords.z);
        var x0 = coords.x * size.x * bpp;
        var z0 = coords.y * size.y * bpp;
        var span = size.x * bpp;
        var chunkPixels = 16 / bpp;
        var ctx = canvas.getContext("2d");

        function lines(step, color, width, alpha) {
          ctx.strokeStyle = color;
          ctx.globalAlpha = alpha;
          ctx.lineWidth = width;
          ctx.beginPath();
          for (var bx = Math.ceil(x0 / step) * step; bx <= x0 + span; bx += step) {
            var px = Math.round((bx - x0) / bpp) + 0.5;
            ctx.moveTo(px, 0); ctx.lineTo(px, size.y);
          }
          for (var bz = Math.ceil(z0 / step) * step; bz <= z0 + span; bz += step) {
            var pz = Math.round((bz - z0) / bpp) + 0.5;
            ctx.moveTo(0, pz); ctx.lineTo(size.x, pz);
          }
          ctx.stroke();
        }

        if (chunkPixels >= CFG.minChunkPixels) {
          lines(16, CFG.color, 1, CFG.opacity);
        }
        if (CFG.regionLines) {
          lines(512, CFG.regionColor, 2, Math.min(1, CFG.opacity + 0.35));
        }
        if (CFG.labels && chunkPixels >= 64) {
          ctx.globalAlpha = Math.min(1, CFG.opacity + 0.4);
          ctx.fillStyle = CFG.color;
          ctx.font = Math.min(14, Math.round(chunkPixels / 7)) + "px sans-serif";
          ctx.textBaseline = "top";
          for (var cx = Math.ceil(x0 / 16) * 16; cx < x0 + span; cx += 16) {
            for (var cz = Math.ceil(z0 / 16) * 16; cz < z0 + span; cz += 16) {
              ctx.fillText((cx >> 4) + ", " + (cz >> 4), (cx - x0) / bpp + 4, (cz - z0) / bpp + 3);
            }
          }
        }
        return canvas;
      }
    });

    var grid = new Grid({ pane: "mrchunksGrid", tileSize: 256, updateWhenZooming: false, maxZoom: 30 });

    // Toggle button (bottom right), remembered per visitor.
    var Toggle = leaflet.Control.extend({
      options: { position: "bottomright" },
      onAdd: function () {
        var box = leaflet.DomUtil.create("label", "leaflet-bar");
        box.style.cssText = "background:#fff;color:#222;padding:4px 8px;font:12px sans-serif;cursor:pointer;display:flex;gap:6px;align-items:center;";
        var input = leaflet.DomUtil.create("input", "", box);
        input.type = "checkbox";
        input.checked = remembered();
        box.appendChild(document.createTextNode(CFG.label));
        leaflet.DomEvent.disableClickPropagation(box);
        leaflet.DomEvent.on(input, "change", function () {
          remember(input.checked);
          if (input.checked) { grid.addTo(map); } else { map.removeLayer(grid); }
        });
        return box;
      }
    });
    new Toggle().addTo(map);
    if (remembered()) {
      grid.addTo(map);
    }

    // squaremap swaps its tile layers regularly; only a world change with other zoom levels needs a redraw.
    var lastMax = nativeMaxZoom(map);
    map.on("layeradd", function (event) {
      if (!(event.layer instanceof leaflet.TileLayer)) {
        return;
      }
      var max = nativeMaxZoom(map);
      if (max !== lastMax) {
        lastMax = max;
        if (map.hasLayer(grid)) {
          grid.redraw();
        }
      }
    });
  }
})();
