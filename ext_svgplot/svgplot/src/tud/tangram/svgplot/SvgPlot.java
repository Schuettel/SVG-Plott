package tud.tangram.svgplot;


import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

import tud.tangram.svgplot.coordinatesystem.CoordinateSystem;
import tud.tangram.svgplot.coordinatesystem.Point;
import tud.tangram.svgplot.coordinatesystem.Range;
import tud.tangram.svgplot.plotting.Function;
import tud.tangram.svgplot.plotting.Gnuplot;
import tud.tangram.svgplot.plotting.Plot;
import tud.tangram.svgplot.plotting.PlotList;
import tud.tangram.svgplot.plotting.PlotList.Overlay;
import tud.tangram.svgplot.plotting.PlotList.OverlayList;
import tud.tangram.svgplot.xml.HtmlDocument;
import tud.tangram.svgplot.xml.SvgDocument;

import com.beust.jcommander.IDefaultProvider;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.IStringConverterFactory;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
/**
 * 
 * @author Gregor Harlan
 * Idea and supervising by Jens Bornschein jens.bornschein@tu-dresden.de
 * Copyright by Technische Universitšt Dresden / MCI 2014
 *
 */
@Parameters(separators = "=", resourceBundle = "Bundle")
public class SvgPlot {

	@Parameter(description = "functions")
	private List<Function> functions = new ArrayList<>();

	@Parameter(names = { "--size", "-s" }, descriptionKey = "param.size")
	private Point size = new Point(210, 297);

	@Parameter(names = { "--xrange", "-x" }, descriptionKey = "param.xrange")
	private Range xRange = new Range(-8, 8);

	@Parameter(names = { "--yrange", "-y" }, descriptionKey = "param.yrange")
	private Range yRange = new Range(-8, 8);

	@Parameter(names = { "--pi", "-p" }, descriptionKey = "param.pi")
	private boolean pi = false;

	@Parameter(names = { "--xlines" }, descriptionKey = "param.xlines")
	private String xLines = null;

	@Parameter(names = { "--ylines" }, descriptionKey = "param.ylines")
	private String yLines = null;

	@Parameter(names = { "--title", "-t" }, descriptionKey = "param.title")
	private String title = null;

	@Parameter(names = { "--gnuplot", "-g" }, descriptionKey = "param.gnuplot")
	private String gnuplot = null;

	@Parameter(names = { "--css", "-c" }, descriptionKey = "param.css")
	private String css = null;

	@Parameter(names = { "--output", "-o" }, descriptionKey = "param.output")
	private File output = null;

	@Parameter(names = { "--help", "-h" }, help = true, descriptionKey = "param.help")
	private boolean help;
	
	//TODO: add parameter and description for integrals
	
	//TODO: add parameter for scatter plot file
	
	//TODO: add a parameter for axes naming
	
	//TODO: add parameter for marking some points 

	private SvgDocument doc;

	private Element viewbox;

	private SvgDocument legend;

	private HtmlDocument desc;

	private CoordinateSystem cs;

	final private double strokeWidth = 0.5;

	final private int[] margin = { 20, 10, 10, 10 };

	private static DecimalFormat decimalFormat = null;

	final private static ResourceBundle bundle = ResourceBundle.getBundle("Bundle");

	public void run() throws ParserConfigurationException, IOException, InterruptedException, TransformerException {
		if (title == null && output != null) {
			title = output.getName();
		}
		String legendTitle = translate("legend") + ": " + title;

		doc = new SvgDocument(title, size, margin[1]);
		legend = new SvgDocument(legendTitle, size, margin[1]);
		legend.setAttribute("id", "legend");
		desc = new HtmlDocument(translate("desc") + ": " + title);

		createCss(doc);
		createCss(legend);

		Point pos = createTitle(doc, title);
		Point legendPos = createTitle(legend, legendTitle);

		xRange.from = Math.min(0, xRange.from);
		xRange.to = Math.max(0, xRange.to);
		yRange.from = Math.min(0, yRange.from);
		yRange.to = Math.max(0, yRange.to);
		int[] margin = this.margin.clone();
		margin[0] = (int) pos.y + 17;
		margin[1] += 20;
		margin[3] += 10;
		cs = new CoordinateSystem(xRange, yRange, size, margin);

		createViewbox();
		createGrid();
		createAxes();
		createReferenceLines();
		createPlots();
		createLegend(legendPos);

		if (output != null) {
			doc.writeTo(new FileOutputStream(output));
			String parent = output.getParent() == null ? "" : output.getParent() +  "\\";
			String legendFile = parent + output.getName().replaceFirst("(\\.[^.]*)?$", "_legend$0");
			legend.writeTo(new FileOutputStream(legendFile));
			String descFile = parent + output.getName().replaceFirst("(\\.[^.]*)?$", "_desc.html");
			desc.writeTo(new FileOutputStream(descFile));
		} else {
			doc.writeTo(System.out);
		}
	}

	/**
	 * Generates the basic css optimized for tactile output on a tiger embosser.
	 * TODO: make this more general - maybe load this from the configuration
	 * @param doc
	 * @throws IOException
	 */
	private void createCss(SvgDocument doc) throws IOException {
		String css = "/* default */\n";
		css += "svg { fill: none; stroke: #000000; stroke-width: " + strokeWidth + "; }\n";
		css += "text { font-family: 'Braille DE Computer'; font-size: 36pt; fill: black; stroke: none; }\n";
		css += "#grid { stroke: #777777; }\n";
		css += "#axes, #reference-lines { stroke: #111111; fill: transparent; }\n";
		double width = 2 * strokeWidth;
		css += "#plots { stroke-width: " + width + "; stroke-dasharray: " + width * 5 + ", " + width * 5 + "; }\n";
		css += "#plot-1 { stroke-dasharray: none; }\n";
		css += "#plot-2 { stroke-dasharray: " + width + ", " + width * 3 + "; }\n";
		css += "#overlays { stroke: none; stroke-dasharray: none; fill: transparent; }";

		if (this.css != null) {
			css += "\n\n/* custom */\n";
			if (new File(this.css).isFile()) {
				this.css = new String(Files.readAllBytes(Paths.get(this.css)));
			}
			css += this.css;
		}
		doc.appendCss(css);
	}

	private Point createTitle(SvgDocument doc, String text) {
		Point pos = new Point(margin[3], margin[0] + 10);
		Element title = (Element) doc.appendChild(doc.createText(pos, text));
		title.setAttribute("id", "title");
		return pos;
	}

	private void createViewbox() {
		viewbox = (Element) doc.appendChild(doc.createElement("svg"));
		viewbox.setAttribute("viewBox", "0 0 " + format2svg(size.x) + " " + format2svg(size.y));

		Node defs = viewbox.appendChild(doc.createElement("defs"));

		Node clipPath = defs.appendChild(doc.createElement("clipPath", "plot-area"));
		Element rect = (Element) clipPath.appendChild(doc.createElement("rect"));
		Point topLeft = cs.convert(cs.xAxis.range.from, cs.yAxis.range.to);
		Point bottomRight = cs.convert(cs.xAxis.range.to, cs.yAxis.range.from);
		rect.setAttribute("x", topLeft.x());
		rect.setAttribute("y", topLeft.y());
		rect.setAttribute("width", format2svg(bottomRight.x - topLeft.x));
		rect.setAttribute("height", format2svg(bottomRight.y - topLeft.y));
	}

	private void createGrid() {
		Node grid = viewbox.appendChild(doc.createGroup("grid"));

		Element xGrid = (Element) grid.appendChild(doc.createGroup("x-grid"));
		double dotDistance = cs.convertYDistance(cs.yAxis.gridInterval);
		int factor = (int) (dotDistance / 2.3);
		dotDistance = (dotDistance - factor * strokeWidth) / factor;
		xGrid.setAttribute("stroke-dasharray", strokeWidth + ", " + format2svg(dotDistance));
		for (double pos : cs.xAxis.gridLines()) {
			Point from = cs.convert(pos, cs.yAxis.range.to, 0, -strokeWidth / 2);
			Point to = cs.convert(pos, cs.yAxis.range.from, 0, strokeWidth / 2);
			xGrid.appendChild(doc.createLine(from, to));
		}

		Element yGrid = (Element) grid.appendChild(doc.createGroup("y-grid"));
		dotDistance = cs.convertXDistance(cs.xAxis.gridInterval);
		factor = (int) (dotDistance / 2.3);
		dotDistance = (dotDistance - factor * strokeWidth) / factor;
		yGrid.setAttribute("stroke-dasharray", strokeWidth + ", " + format2svg(dotDistance));
		for (double pos : cs.yAxis.gridLines()) {
			Point from = cs.convert(cs.xAxis.range.from, pos, -strokeWidth / 2, 0);
			Point to = cs.convert(cs.xAxis.range.to, pos, strokeWidth / 2, 0);
			yGrid.appendChild(doc.createLine(from, to));
		}
	}

	/**
	 * Paint the axes to the svg file.
	 */
	private void createAxes() {
		//TODO: name the axes
		
		Node axes = viewbox.appendChild(doc.createGroup("axes"));

		Point from, to;
		String points;

		from = cs.convert(xRange.from, 0, -10, 0);
		to = cs.convert(xRange.to, 0, 10, 0);
		Element xAxisLine = doc.createLine(from, to);
		axes.appendChild(xAxisLine);
		xAxisLine.setAttribute("id", "x-axis");
		Element xAxisArrow = doc.createElement("polyline", "x-axis-arrow");
		axes.appendChild(xAxisArrow);
		to.translate(0, -3);
		points = to.toString();
		to.translate(5.2, 3);
		points += " " + to;
		to.translate(-5.2, 3);
		points += " " + to;
		xAxisArrow.setAttribute("points", points);
		xAxisArrow.appendChild(doc.createTitle(translate("xaxis")));
		
		// append x-label
		Point pos2 = to;
		pos2.translate(0, 13);
		doc.appendChild(createLabel("x", pos2, "x_label", "label"));


		from = cs.convert(0, yRange.from, 0, 10);
		to = cs.convert(0, yRange.to, 0, -10);
		Element yAxisLine = doc.createLine(from, to);
		axes.appendChild(yAxisLine);
		yAxisLine.setAttribute("id", "y-axis");
		Element yAxisArrow = doc.createElement("polyline", "y-axis-arrow");
		axes.appendChild(yAxisArrow);
		to.translate(-3, 0);
		points = to.toString();
		to.translate(3, -5.2);
		points += " " + to;
		to.translate(3, 5.2);
		points += " " + to;
		yAxisArrow.setAttribute("points", points);
		yAxisArrow.appendChild(doc.createTitle(translate("yaxis")));
		
		
		/*
		 *  create x title
		 */
		
		Point pos3 = to;
		pos3.translate(-15, 0);
		doc.appendChild(createLabel("y", pos3, "y_label", "label"));
		
		

		Node xTics = axes.appendChild(doc.createGroup("x-tics"));
		for (double pos : cs.xAxis.ticLines()) {
			from = cs.convert(pos, 0, 0, -6);
			to = from.clone();
			to.translate(0, 12);
			xTics.appendChild(doc.createLine(from, to));
		}

		Node yTics = axes.appendChild(doc.createGroup("y-tics"));
		for (double pos : cs.yAxis.ticLines()) {
			from = cs.convert(0, pos, -6, 0);
			to = from.clone();
			to.translate(12, 0);
			yTics.appendChild(doc.createLine(from, to));
		}
	}
	
	private Element createLabel(String text, Point pos, String id, String cssClass){
		Element label = doc.createText(pos, text);
		if(id != null && !id.isEmpty())label.setAttribute("id", id);
		if(cssClass != null && !cssClass.isEmpty())label.setAttribute("class", cssClass);
		return label;
	}

	private void createReferenceLines() {
		if (xLines == null && yLines == null) {
			return;
		}

		Node referenceLines = viewbox.appendChild(doc.createGroup("reference-lines"));

		if (xLines != null) {
			Node group = referenceLines.appendChild(doc.createGroup("x-reference-lines"));
			for (String line : xLines.trim().split("\\s+")) {
				double pos = Double.parseDouble(line);
				Point from = cs.convert(pos, cs.yAxis.range.to, 0, -strokeWidth / 2);
				Point to = cs.convert(pos, cs.yAxis.range.from, 0, strokeWidth / 2);
				group.appendChild(doc.createLine(from, to));
			}
		}

		if (yLines != null) {
			Node group = referenceLines.appendChild(doc.createGroup("y-reference-lines"));
			for (String line : yLines.trim().split("\\s+")) {
				double pos = Double.parseDouble(line);
				Point from = cs.convert(cs.xAxis.range.from, pos, -strokeWidth / 2, 0);
				Point to = cs.convert(cs.xAxis.range.to, pos, strokeWidth / 2, 0);
				group.appendChild(doc.createLine(from, to));
			}
		}
	}

	private void createPlots() throws IOException, InterruptedException {
		//TODO: add scatter plot
		
		
		Node plots = viewbox.appendChild(doc.createGroup("plots"));

		Gnuplot gnuplot = new Gnuplot(this.gnuplot);
		gnuplot.setSample(cs.xAxis.atomCount);
		gnuplot.setSample(1300);
		gnuplot.setXRange(cs.xAxis.range, pi);
		gnuplot.setYRange(cs.yAxis.range);

		int i = 1;
		PlotList plotList = new PlotList(cs);
		for (Function function : functions) {
			Node graph = plots.appendChild(doc.createGroup("plot-" + i++));
			Element path = (Element) graph.appendChild(doc.createElement("path"));
			path.setAttribute("clip-path", "url(#plot-area)");

			String points = "";
			Plot plot = new Plot(function, gnuplot);
			plotList.add(plot);
			for (List<Point> list : plot) {
				String op = "M";
				for (Point point : list) {
					points += op + cs.convert(point) + " ";
					op = "L";
				}
			}
			path.setAttribute("d", points);
		}

		Node overlaysElement = viewbox.appendChild(doc.createGroup("overlays"));
		OverlayList overlays = plotList.overlays();
		for (Function function : functions) {
			for (Overlay overlay : overlays) {
				if (function.equals(overlay.getFunction())) {
					overlaysElement.appendChild(createOverlay(overlay));
				}
			}
		}
		for (Overlay overlay : overlays) {
			if (overlay.getFunction() == null) {
				overlaysElement.appendChild(createOverlay(overlay));
			}
		}

		createDesc(plotList);
	}

	private Element createOverlay(Overlay overlay) {
		//TODO: create overlays for origin
		
		Element circle = doc.createCircle(cs.convert(overlay), Overlay.RADIUS);
		circle.appendChild(doc.createTitle(format(overlay)));
		if (overlay.getFunction() != null) {
			circle.appendChild(doc.createDesc(overlay.getFunction().toString()));
		}
		return circle;
	}

	/**
	 * Writes the external HTML description document
	 * @param plotList
	 */
	private void createDesc(PlotList plotList) {
		String tab = "    ";
		String nl = "\n" + tab + tab;

		Node div = desc.appendBodyChild(desc.createDiv("functions"));
		div.appendChild(desc.createP(translateN("desc.intro", formatX(cs.xAxis.range.from), formatX(cs.xAxis.range.to), formatX(cs.xAxis.ticInterval), formatY(cs.yAxis.range.from), formatY(cs.yAxis.range.to), formatY(cs.yAxis.ticInterval), functions.size())));

		if (!functions.isEmpty()) {
			Node ol = div.appendChild(desc.createElement("ul"));
			int f = 0;
			for (Function function : functions) {
				Element li = (Element) ol.appendChild(desc.createElement("li"));
				li.appendChild(desc.createTextElement("span", "f_" + (++f) + "(x) = "));
				if (function.hasTitle()) {
					li.appendChild(desc.createTextElement("strong", function.getTitle() + ":"));
					li.appendChild(desc.createTextNode(" " + function.getFunction() + nl + tab + tab));
				} else {
					li.appendChild(desc.createTextElement("span", function.getFunction()));
				}
			}

			if (functions.size() > 1) {
				div = desc.appendBodyChild(desc.createDiv("intersections"));
				boolean hasIntersections = false;
				for (int i = 0; i < plotList.size() - 1; i++) {
					for (int k = i + 1; k < plotList.size(); k++) {
						List<Point> intersections = plotList.get(i).getIntersections(plotList.get(k));
						if (!intersections.isEmpty()) {
							hasIntersections = true;
							div.appendChild(desc.createP(translateN("desc.intersections", "f_"+(i + 1), "f_"+(k + 1), intersections.size())));
							div.appendChild(createPointList(intersections, "s"));
						}
					}
				}
				if (!hasIntersections) {
					div.appendChild(desc.createP(translate("desc.intersections_0")));
				}
			}
		}

		for (int i = 0; i < plotList.size(); i++) {
			div = desc.appendBodyChild(desc.createDiv("function-" + (i + 1)));
			List<Point> extrema = plotList.get(i).getExtrema();
			String f = plotList.size() == 1 ? "" : " f_" + (i + 1);
			div.appendChild(desc.createP(translateN("desc.extrema", f, extrema.size())));
			if (!extrema.isEmpty()) {
				div.appendChild(createPointList(extrema, "E"));
			}
			List<Point> roots = plotList.get(i).getRoots();
			div.appendChild(desc.createP(translateN("desc.roots", roots.size())));
			if (!roots.isEmpty()) {
				div.appendChild(createXPointList(roots));
			}
		}

		desc.appendBodyChild(desc.createP(translate("desc.note")));
	}

	/**
	 * Generates a HTML ul list with the Points as li list entries (x / y)
	 * @param points
	 * @return ul element with points as a list
	 */
	private Element createPointList(List<Point> points) {
			return createPointList(points, null);
	}
	/**
	 * Generates a HTML ul list with the Points as li list entries packed in the given caption string and brackets. E.g. E(x|y)
	 * @param points
	 * @return ul element with points as a list
	 */
	private Element createPointList(List<Point> points, String cap) {
		Element list = desc.createElement("ul");
		int i = 0;
		for (Point point : points) {
			if(cap != null && !cap.isEmpty()){
				list.appendChild(desc.createTextElement("li", formatForText(point, cap+"_" + ++i)));				
				}
			else{
				list.appendChild(desc.createTextElement("li", format(point)));
			}
		}
		return list;
	}
	
	
	/**
	 * Generates a HTML ul list with the Points as li list entries (x / y)
	 * @param points
	 * @return ul element with points as a list
	 */
	private Element createXPointList(List<Point> points) {
			return createXPointList(points, "x");
	}
	/**
	 * Generates a HTML ul list with the Points as li list entries packed in the given caption string and brackets. E.g. E(x|y)
	 * @param points
	 * @return ul element with points as a list
	 */
	private Element createXPointList(List<Point> points, String cap) {
		Element list = desc.createElement("ul");
		int i = 0;
		for (Point point : points) {
			if(cap != null && !cap.isEmpty()){
				list.appendChild(desc.createTextElement("li", cap+"_" + ++i + " = " + formatX(point.x)));				
				}
			else{
				list.appendChild(desc.createTextElement("li", formatX(point.x)));
			}
		}
		return list;
	}
	

	private void createLegend(Point pos) {
		//TODO: change key
		
		int distance = 7;
		pos.y += 2 * distance;

		Element viewbox = (Element) legend.appendChild(legend.createElement("svg"));
		viewbox.setAttribute("viewBox", "0 0 " + format2svg(size.x) + " " + format2svg(size.y));

		Node plots = viewbox.appendChild(legend.createGroup("plots"));
		int i = 1;
		for (Function function : functions) {
			Node plot = plots.appendChild(legend.createGroup("plot-" + i++));
			plot.appendChild(legend.createLine(new Point(pos.x, pos.y - 5), new Point(pos.x + 26, pos.y - 5)));

			pos.translate(35, 0);
			if (function.hasTitle()) {
				legend.appendChild(legend.createText(pos, "f_" +(i-1) +"(x) = "+ function.getTitle() + ":", function.getFunction()));
			} else {
				legend.appendChild(legend.createText(pos,  "f_" +(i-1) +"(x) = "+function.getFunction()));
			}
			pos.translate(-35, distance);
		}

		pos.translate(0, distance);
		legend.appendChild(legend.createText(pos, translate("legend.xrange", formatX(cs.xAxis.range.from), formatX(cs.xAxis.range.to)), translate("legend.xtic", formatX(cs.xAxis.ticInterval))));

		pos.translate(0, distance);
		legend.appendChild(legend.createText(pos, translate("legend.yrange", formatY(cs.yAxis.range.from), formatY(cs.yAxis.range.to)), translate("legend.ytic", formatY(cs.yAxis.ticInterval))));
	}

	/**
	 * Formats the x value of a point with respect to in Pi is set.
	 * @param x	|	x-value
	 * @return formated string for the point
	 */
	public String formatX(double x) {
		String str = cs.xAxis.format(x);
		if (pi && !"0".equals(str)) {
			str += " pi";
		}
		return str;
	}

	public String formatY(double y) {
		return cs.yAxis.format(y);
	}

	/**
	 * Formats a Point that it is optimized for speech output.
	 * E.g. (x / y)
	 * @param point	|	The point that should be transformed into a textual representation
	 * @return formated string for the point with '/' as delimiter
	 */
	public String format(Point point) {
		return formatX(point.x) + " / " + formatY(point.y);
	}
	
	/**
	 * Formats a Point that it is optimized for textual output and packed into the caption with brackets.
	 * E.g. E(x | y)
	 * @param point	|	The point that should be transformed into a textual representation
	 * @param cap	|	The caption sting without brackets
	 * @return formated string for the point with '/' as delimiter if now caption is set, otherwise packed in the caption with brackets and the '|' as delimiter
	 */
	public String formatForText(Point point, String cap) {
		String p = formatX(point.x) + " | " + formatY(point.y);
		cap = cap.trim();
		return (cap != null && !cap.isEmpty()) ? cap + "(" + p + ")" : p;
	}

	public static String translate(String key, Object... arguments) {
		return MessageFormat.format(bundle.getString(key), arguments);
	}

	public static String translateN(String key, Object... arguments) {
		int last = (int) arguments[arguments.length - 1];
		String suffix = last == 0 ? "_0" : last == 1 ? "_1" : "_n";
		return translate(key + suffix, arguments);
	}

	public static String format2svg(double value) {
		if (decimalFormat == null) {
			decimalFormat = new DecimalFormat("0.###");
			DecimalFormatSymbols dfs = new DecimalFormatSymbols();
			dfs.setDecimalSeparator('.');
			decimalFormat.setDecimalFormatSymbols(dfs);
		}
		return decimalFormat.format(value);
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		SvgPlot plot = new SvgPlot();
		JCommander jc = new JCommander(plot);
		jc.addConverterFactory(new SvgPlot.StringConverterFactory());

		try {
			final Properties properties = new Properties();
			BufferedInputStream stream = new BufferedInputStream(new FileInputStream("svgplot.properties"));
			properties.load(stream);
			stream.close();

			jc.setDefaultProvider(new IDefaultProvider() {
				@Override
				public String getDefaultValueFor(String optionName) {
					return properties.getProperty(optionName.replaceFirst("^-+", ""));
				}
			});
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
		}

		for (int i = 0; i < args.length; i++) {
			if (args[i].matches("\\s*-[^-][^=:,]+")) {
				args[i] = "\\" + args[i].trim();
			}
		}

		jc.parse(args);

		if (plot.help) {
			jc.setProgramName("java -jar svgplot.jar");
			jc.usage();
			return;
		}

		try {
			plot.run();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static class StringConverterFactory implements IStringConverterFactory {
		@SuppressWarnings({ "unchecked", "rawtypes" })
		public Class<? extends IStringConverter<?>> getConverter(Class forType) {
			if (forType.equals(Point.class))
				return Point.Converter.class;
			else if (forType.equals(Range.class))
				return Range.Converter.class;
			else if (forType.equals(Function.class))
				return Function.Converter.class;
			else
				return null;
		}
	}
}
