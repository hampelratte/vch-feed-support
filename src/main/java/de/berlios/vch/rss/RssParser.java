package de.berlios.vch.rss;

import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;

import org.jdom.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;

import com.sun.syndication.feed.module.mediarss.MediaEntryModule;
import com.sun.syndication.feed.module.mediarss.io.MediaModuleParser;
import com.sun.syndication.feed.module.mediarss.types.MediaContent;
import com.sun.syndication.feed.module.mediarss.types.Metadata;
import com.sun.syndication.feed.module.mediarss.types.Thumbnail;
import com.sun.syndication.feed.module.mediarss.types.UrlReference;
import com.sun.syndication.feed.synd.SyndContent;
import com.sun.syndication.feed.synd.SyndContentImpl;
import com.sun.syndication.feed.synd.SyndEnclosure;
import com.sun.syndication.feed.synd.SyndEnclosureImpl;
import com.sun.syndication.feed.synd.SyndEntry;
import com.sun.syndication.feed.synd.SyndFeed;
import com.sun.syndication.io.FeedException;
import com.sun.syndication.io.SyndFeedInput;
import com.sun.syndication.io.XmlReader;

public class RssParser {
    private static transient Logger logger = LoggerFactory.getLogger(RssParser.class);

    public static SyndFeed parseUri(String uri) throws IllegalArgumentException, MalformedURLException, FeedException, IOException {
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(new URL(uri)));
        convertYahooMedia(feed);
        removeNonVideoItems(feed);
        if (feed.getLink() == null) {
            feed.setLink(uri);
        }
        return feed;
    }

    public static SyndFeed parse(String rss) throws IllegalArgumentException, MalformedURLException, FeedException, IOException {
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new InputSource(new StringReader(rss)));
        convertYahooMedia(feed);
        removeNonVideoItems(feed);
        return feed;
    }

    private static void convertYahooMedia(SyndFeed feed) {
        @SuppressWarnings("unchecked")
        List<SyndEntry> entries = feed.getEntries();
        for (SyndEntry entry : entries) {
            @SuppressWarnings("unchecked")
            List<Element> fms = (List<Element>) entry.getForeignMarkup();

            MediaEntryModule module = null;
            for (Element element : fms) {
                if (MediaEntryModule.URI.equals(element.getNamespaceURI())) {
                    MediaModuleParser parser = new MediaModuleParser();
                    module = (MediaEntryModule) parser.parse(element);
                    break;
                }
            }

            if (module == null) {
                module = (MediaEntryModule) entry.getModule(MediaEntryModule.URI);

                if (module == null) {
                    continue;
                }
            }

            MediaContent[] contents = module.getMediaContents();
            if (contents.length == 0) {
                if (module.getMediaGroups().length > 0) {
                    contents = module.getMediaGroups()[0].getContents();
                }
            }

            convertYahooMediaToEnclosure(entry, contents);
            convertYahooMediaThumbnails(entry, module);
            if (entry.getDescription() == null || entry.getDescription().getValue() == null || entry.getDescription().getValue().isEmpty()) {
                convertYahooMediaDescription(entry, module.getMetadata());
            }
        }
    }

    private static void convertYahooMediaDescription(SyndEntry entry, Metadata meta) {
        if (meta != null && meta.getDescription() != null) {
            String description = meta.getDescription();
            SyndContent desc = new SyndContentImpl();
            desc.setValue(description);
            desc.setType(meta.getDescriptionType());
            entry.setDescription(desc);
        }
    }

    @SuppressWarnings("unchecked")
    private static void convertYahooMediaThumbnails(SyndEntry entry, MediaEntryModule module) {
        Metadata meta = module.getMetadata();
        if (meta != null && (meta.getThumbnail() == null || meta.getThumbnail().length == 0)) {
            if (module.getMediaGroups().length > 0) {
                meta = module.getMediaGroups()[0].getMetadata();
            }
        }

        if (meta != null && meta.getThumbnail() != null) {
            Thumbnail[] thumbs = meta.getThumbnail();
            if (thumbs.length > 0) {
                Thumbnail thumb = thumbs[0];
                Element elem = new Element("thumbnail");
                elem.setText(thumb.getUrl().toString());
                ((List<Element>) entry.getForeignMarkup()).add(elem);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static void convertYahooMediaToEnclosure(SyndEntry entry, MediaContent[] contents) {
        if (contents.length > 0) {
            for (int i = 0; i < contents.length; i++) {
                MediaContent content = contents[i];
                if (!(content.getReference() instanceof UrlReference)) {
                    continue;
                }

                SyndEnclosure enc = new SyndEnclosureImpl();
                enc.setUrl(content.getReference().toString());
                if (content.getType() != null) {
                    enc.setType(content.getType());
                } else {
                    enc.setType("video");
                }

                if (content.getDuration() != null) {
                    // set duration in foreign markup
                    Element elem = new Element("duration");
                    elem.setText(content.getDuration().toString());
                    ((List<Element>) entry.getForeignMarkup()).add(elem);
                }

                if (content.getFileSize() != null) {
                    enc.setLength(content.getFileSize());
                }

                if (enc.getUrl().length() > 0) {
                    entry.getEnclosures().add(enc);
                }
            }
        }
    }

    /**
     * Removes all items which don't have an video enclosure
     * 
     * @param feed
     */
    private static void removeNonVideoItems(SyndFeed feed) {
        for (Iterator<?> iterator = feed.getEntries().iterator(); iterator.hasNext();) {
            SyndEntry entry = (SyndEntry) iterator.next();
            boolean hasVideo = false;
            for (Iterator<?> encIter = entry.getEnclosures().iterator(); encIter.hasNext();) {
                SyndEnclosure enclosure = (SyndEnclosure) encIter.next();
                if (enclosure.getType() != null && enclosure.getType().startsWith("video")) {
                    hasVideo = true;
                    break;
                }
            }
            if (!hasVideo && entry.getEnclosures().size() > 0) {
                logger.debug("Removing item {} from feed {}, because it has no video enclosure", entry.getTitle(), feed.getTitle());
                iterator.remove();
            }
        }
    }
}
