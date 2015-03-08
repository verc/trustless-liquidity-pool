/*
 * Copyright (C) 2014 desrever <desrever at nubits.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package com.nubits.nubot.utils.logging;

/**
 *
 * @author desrever , credits
 * http://www.vogella.com/articles/Logging/article.html#overview_logger
 */
import com.nubits.nubot.utils.Utils;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

//This custom formatter formats parts of a log record to a single line
class LogFormatterHTML extends Formatter {
    // This method is called for every log records

    private static final String PATH_TO_ASSETS = "../../res/logs_assets/";

    public String format(LogRecord rec) {
        if (rec.getLevel().intValue() >= Level.INFO.intValue()) {
            StringBuffer buf = new StringBuffer(1000);
            buf.append("<tr>");

            String CSSclass = " class='info mes'";

            if (rec.getLevel().intValue() == Level.WARNING.intValue()) {
                CSSclass = " class='trades mes'";
            } else if (rec.getLevel().intValue() == Level.SEVERE.intValue()) {
                CSSclass = " class='errors mes'";
            }


            String message = "<td" + CSSclass + "  rel='message'>" + formatMessage(rec);
            buf.append(message);

            buf.append("</td>\n");


            buf.append("<td class='clas' rel='class'>");
            buf.append(rec.getLoggerName());
            buf.append("</td>\n");

            buf.append("<td class='met' rel='method'>");
            buf.append(rec.getSourceMethodName());
            buf.append("</td>\n");


            buf.append("<td class='tim' rel='time'>");
            buf.append(Utils.calcDate(rec.getMillis()));
            buf.append("</td>\n");

            buf.append("</tr>\n");
            return buf.toString();
        } else {
            return "";
        }


    }

    // This method is called just after the handler using this
    // formatter is created
    public String getHead(Handler h) {
        return "<!DOCTYPE HTML>\n"
                + "<html>\n"
                + "<head>\n"
                + "	<title>NuBot Logs</title>\n"
                + "	<link rel=\"stylesheet\" type=\"text/css\" href=\"" + PATH_TO_ASSETS + "css/style.css\">\n"
                + "	<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n"
                + "</head>\n"
                + "<body>\n"
                + "<section class=\"container\">\n"
                + "	<section class=\"title\">\n"
                + "		<h1>NuBot logs " + new Date() + "</h1>	\n"
                + "	</section>\n"
                + "\n"
                + "	<section class=\"filter\">\n"
                + "		<table>	\n"
                + "			<thead>\n"
                + "				<tr class=\"first\">\n"
                + "					<th scope=\"col\" class=\"tableHeader\">Filter</th>\n"
                + "				</tr>\n"
                + "			</thead>\n"
                + "			\n"
                + "			<tbody>\n"
                + "				<tr class=\"first\">\n"
                + "					<th class=\"filterBox\">\n"
                + "						Show:<br>\n"
                + "						<input class=\"css-checkbox lrg\" rel=\"info\" type=\"checkbox\" value=\"info\" id=\"chkb01\" checked=\"checked\"> \n"
                + "							<label for=\"chkb01\" class=\"css-label lrg vlad\">Info</label>\n"
                + "						<br/>\n"
                + "						<input id=\"chkb02\" class=\"css-checkbox lrg\"rel=\"trades\" type=\"checkbox\" value=\"trades\" checked=\"checked\"> \n"
                + "							<label for=\"chkb02\" class=\"css-label lrg vlad\">Warnings</label>\n"
                + "						<br/>\n"
                + "						<input id=\"chkb03\" class=\"css-checkbox lrg\"rel=\"errors\" type=\"checkbox\" value=\"errors\" checked=\"checked\"> \n"
                + "							<label for=\"chkb03\" class=\"css-label lrg vlad\">Errors</label>\n"
                + "						<br/>\n"
                + "					</th>\n"
                + "				</tr>\n"
                + "			</tbody>\n"
                + "		</table>\n"
                + "	</section>\n"
                + "	<table>\n"
                + "		<!-- Table header -->\n"
                + "	\n"
                + "		<thead>\n"
                + "			<tr class=\"first\">\n"
                + "				<th scope=\"col\" class=\"tableHeader mes\">Message</th>\n"
                + "				<th scope=\"col\" class=\"tableHeader cla\">Class</th>\n"
                + "				<th scope=\"col\" class=\"tableHeader met\">Method</th>\n"
                + "				<th scope=\"col\" class=\"tableHeader tim\">Time</th>\n"
                + "			</tr>\n"
                + "		</thead>\n"
                + "	\n"
                + "		<!-- Table body -->		\n"
                + "		<tbody>";
    }

    // This method is called just after the handler using this
    // formatter is closed
    public String getTail(Handler h) {
        return "</tbody>\n"
                + "			\n"
                + "	</table>\n"
                + "</section>\n"
                + "	\n"
                + "	<script src=\"" + PATH_TO_ASSETS + "js/jquery-2.1.1.min.js\"></script>\n"
                + "	<script type=\"text/javascript\">\n"
                + "		$(\"input:checkbox\").click(function () {\n"
                + "		    $('tr').not('.first').css(\"display\",\"none\"); \n"
                + "		    $('input[type=checkbox]').each(function () {\n"
                + "		        if ($(this)[0].checked) {\n"
                + "		            showAll = false;\n"
                + "		            var status = $(this).attr('rel');\n"
                + "		            $('td.' + status).parent('tr').css(\"display\",\"table-row\"); \n"
                + "		        }\n"
                + "		    });\n"
                + "		    if(showAll){\n"
                + "		        $('tr').css(\"display\",\"table-row\");\n"
                + "		    }\n"
                + "		});\n"
                + "	</script>\n"
                + "</body>\n"
                + "</html>";
    }
}
