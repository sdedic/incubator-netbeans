/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.netbeans.modules.java.lsp.server.protocol;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;
import org.netbeans.modules.parsing.api.Source;
import org.netbeans.modules.parsing.implspi.SourceFactory;
import org.openide.filesystems.FileObject;
import org.openide.util.Lookup;
import org.openide.util.lookup.Lookups;
import org.openide.util.lookup.ServiceProvider;

/**
 *
 * @author sdedic
 */
@ServiceProvider(service = SourceFactory.class, position = 0)
public class TestLspSourceFactory extends SourceFactory {
    private final Map<FileObject, Reference<Source>> instances = new WeakHashMap<>();
    private Lookup systemLookup;

    public TestLspSourceFactory() {
        Lookups.executeWith(null, () -> systemLookup = Lookup.getDefault());
    }

    @Override
    public Source createSource(
            FileObject file,
            String mimeType,
            Lookup context) {
        final Reference<Source> sourceRef = instances.get(file);
        Source source = sourceRef == null ? null : sourceRef.get();
        if (source == null || !mimeType.equals(source.getMimeType())) {
            source = newSource(file, mimeType, systemLookup);
            instances.put(file, new WeakReference<>(source));
        }
        return source;
    }

    @Override
    public Source getSource(final FileObject file) {
        final Reference<Source> ref = instances.get(file);
        return ref == null ? null : ref.get();
    }

    @Override
    public Source removeSource(final FileObject file) {
        final Reference<Source> ref = instances.remove(file);
        return ref == null ? null : ref.get();
    }
}
