/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.java.lsp.server.explorer;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.ImageCapabilities;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import org.openide.modules.OnStart;
import org.openide.util.ImageUtilities;
import static org.openide.util.ImageUtilities.icon2Image;
import org.openide.util.Parameters;

/**
 * Goes through UIDefaults and replaces Icons and Images with variants that
 * return image's URL from the 'url' property.
 * @author sdedic
 */
@OnStart
public class UIDefaultImageDecorator implements Runnable {
    private static final Logger LOG = Logger.getLogger(UIDefaultImageDecorator.class.getName());
    
    public static final String PROP_URI = "uri"; // NOI18N
    public static final String PROP_URL = "url"; // NOI18N
    public static final String PROP_ORIGINAL_ICON = "originalIcon"; // NOI18N
    public static final String PROP_ORIGINAL_IMAGE = "originalImage"; // NOI18N
    
    private static final String URN_DEFAULTS_PREFIX = "urn:uidefaults:"; // NOI18N
    
    private static Image wrapImage(String key, Image image, Object... meta) {
        Object o = image.getProperty("url", null); // NOI18N
        if (o instanceof URL) {
            return image;
        }
        
        o = image.getProperty(PROP_URI, null);
        if (o instanceof String || o instanceof URI) {
            return image;
        } else {
            
            try {
                Object[] a;
                
                if (meta == null || meta.length == 0) {
                    a = new Object[] { PROP_URI, new URI(URN_DEFAULTS_PREFIX + key) };
                } else {
                    a = Arrays.copyOf(meta, meta.length + 2);
                    a[meta.length] = PROP_URI;
                    a[meta.length + 1] = new URI(URN_DEFAULTS_PREFIX + key);
                }
                /*
                return image instanceof ImageIcon ?
                        new MetadataImageIcon(image, a) : 
                        new MetadataImage(image, a);
                */
                return new MetadataImage(image, a);
            } catch (URISyntaxException ex) {
                return image;
            }
        }
    }
    
    @Override
    public void run() {
        EventQueue.invokeLater(() -> decorateUIDefaults());
    }
    
    private static final class IconImageIcon extends ImageIcon {
        private volatile Icon delegate;
        private final String key;
        
        IconImageIcon(String key, Icon delegate) {
            super(wrapImage(key, icon2Image(delegate)));
            Parameters.notNull("delegate", delegate);
            this.delegate = delegate;
            this.key = key;
        }

        @Override
        public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
            delegate.paintIcon(c, g, x, y);
        }

        public Icon getDelegateIcon() {
            return delegate;
        }

        /* NETBEANS-3769: Since ImageIcon implements Serializable, we must support serialization.
        But there is no guarantee that the delegate implements Serializable, thus the default
        serialization mechanism might throw a java.io.NotSerializableException when
        ObjectOutputStream.writeObject gets recursively called on the delegate. Implement a custom
        serialization mechanism based on ImageIcon instead. */

        private void writeObject(ObjectOutputStream out) throws IOException {
            out.writeObject(new ImageIcon(getImage()));
        }

        private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
            this.delegate = (ImageIcon) in.readObject();
        }

        private void readObjectNoData() throws ObjectStreamException {
            this.delegate = new ImageIcon(new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR));
        }
    }
    
    public static void decorateUIDefaults() {
        UIDefaults defs = UIManager.getDefaults();
        
        for (Enumeration en = defs.keys(); en.hasMoreElements(); ) {
            Object ok = en.nextElement();
            Object o = defs.get(ok);
            try {

                if (o instanceof ImageIcon) {
                    ImageIcon ii = (ImageIcon)o;
                    Image wrapper = wrapImage(ok.toString(), ii.getImage(), PROP_ORIGINAL_IMAGE, ii.getImage() 
                            /*, PROP_ORIGINAL_ICON, ii
                            */);
                    if (wrapper != ii.getImage()) {
                        replace(defs, ok, "ImageIcon", new ImageIcon(wrapper));
                    }
                    continue;
                }

                if (o instanceof Icon) {
                    final Icon ico = (Icon)o;
                    Image converted = ImageUtilities.icon2Image(ico);
                    Image wrapper = wrapImage(ok.toString(), converted /*, PROP_ORIGINAL_ICON, ico */);
                    if (wrapper != converted) {
                        replace(defs, ok, "Icon", new ImageIcon(wrapper) {
                            final Icon ico = (Icon)o;

                            @Override
                            public void paintIcon(Component c, Graphics g, int x, int y) {
                                ico.paintIcon(c, g, x, y);
                            }

                            @Override
                            public int getIconWidth() {
                                return ico.getIconWidth();
                            }

                            @Override
                            public int getIconHeight() {
                                return ico.getIconWidth();
                            }
                        });
                    }
                    continue;
                }

                if (o instanceof Image) {
                    Image wrapper = wrapImage(ok.toString(), (Image)o, PROP_ORIGINAL_IMAGE, o);
                    if (o != wrapper) {
                        replace(defs, ok, "Image", new ImageIcon(wrapper)); // NOI18N
                    }
                }
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "Error wrapping default {0}: ", ok.toString());
                LOG.log(Level.WARNING, "Exception thrown", ex);
            }
        }
    }
    
    private static void replace(UIDefaults defs, Object key, String type, Object replacement) {
        LOG.log(Level.INFO, "Replaced {0} {1}", new Object[] { type, key });
        defs.put(key, replacement);
    }
    
    public static final class MetadataImageIcon extends ImageIcon {
        private final Map<String, Object> additionalProperties;
        
        public MetadataImageIcon(Image original, Object... keysAndValues) {
            super(original);
            if (keysAndValues != null) {
                Map<String, Object> m = new HashMap<>(keysAndValues.length / 2);
                for (int i = 0; i < keysAndValues.length; i += 2) {
                    String k = keysAndValues[i].toString();
                    m.put(k, keysAndValues[i + 1]);
                }
                this.additionalProperties = m;
            } else {
                this.additionalProperties = Collections.emptyMap();
            }
        }

        public Object getProperty(String name, ImageObserver observer) {
            if (additionalProperties.containsKey(name)) {
                return additionalProperties.get(name);
            }
            return getImage().getProperty(name, observer);
        }
    }

    /**
     * Delegating Image which adds metadata to the original image's {@link Image#getProperty}.
     * @author sdedic
     */
    public static final class MetadataImage extends Image {
        private final Image original;
        private final Map<String, Object> additionalProperties;

        public MetadataImage(Image original, Map<String, Object> additionalProperties) {
            this.original = original;
            this.additionalProperties = additionalProperties;
        }

        public MetadataImage(Image original, Object... keysAndValues) {
            this.original = original;
            if (keysAndValues != null) {
                Map<String, Object> m = new HashMap<>(keysAndValues.length / 2);
                for (int i = 0; i < keysAndValues.length; i += 2) {
                    String k = keysAndValues[i].toString();
                    m.put(k, keysAndValues[i + 1]);
                }
                this.additionalProperties = m;
            } else {
                this.additionalProperties = Collections.emptyMap();
            }
        }

        @Override
        public int getWidth(ImageObserver observer) {
            return original.getWidth(observer);
        }

        @Override
        public int getHeight(ImageObserver observer) {
            return original.getHeight(observer);
        }

        @Override
        public ImageProducer getSource() {
            return original.getSource();
        }

        @Override
        public Graphics getGraphics() {
            return original.getGraphics();
        }

        @Override
        public Object getProperty(String name, ImageObserver observer) {
            if (additionalProperties.containsKey(name)) {
                return additionalProperties.get(name);
            }
            return original.getProperty(name, observer);
        }
/*        

        public void flush() {
            original.flush();
            super.flush();
        }

        public ImageCapabilities getCapabilities(GraphicsConfiguration gc) {
            return original.getCapabilities(gc);
        }

        public void setAccelerationPriority(float priority) {
            original.setAccelerationPriority(priority);
        }

        public float getAccelerationPriority() {
            return original.getAccelerationPriority();
        }
*/
    }
}
