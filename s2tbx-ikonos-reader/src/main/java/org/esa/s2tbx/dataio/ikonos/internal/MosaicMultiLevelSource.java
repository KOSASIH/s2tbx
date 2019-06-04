package org.esa.s2tbx.dataio.ikonos.internal;

import com.bc.ceres.glevel.support.AbstractMultiLevelSource;
import com.bc.ceres.glevel.support.DefaultMultiLevelModel;
import com.bc.ceres.glevel.support.DefaultMultiLevelSource;
import org.esa.snap.core.datamodel.Band;

import javax.media.jai.*;
import javax.media.jai.operator.BorderDescriptor;
import javax.media.jai.operator.ConstantDescriptor;
import javax.media.jai.operator.MosaicDescriptor;
import javax.media.jai.operator.TranslateDescriptor;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A single banded multi-level mosaic image source.
 *
 * @author Denisa Stefanescu
 */
public class MosaicMultiLevelSource extends AbstractMultiLevelSource {

    private static final Logger logger = Logger.getLogger(MosaicMultiLevelSource.class.getName());

    private final Band sourceBand;
    private final int imageWidth;
    private final int imageHeight;
    private final int tileWidth;
    private final int tileHeight;

    public MosaicMultiLevelSource(final Band sourceBand, final int imageWidth, final int imageHeight,
                                  final int tileWidth, final int tileHeight, final int levels,
                                  final AffineTransform transform) {
        super(new DefaultMultiLevelModel(levels, transform, imageWidth, imageHeight));
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.sourceBand = sourceBand;
    }

    private PlanarImage createTileImage(final int level) throws IOException {
        return (PlanarImage) this.sourceBand.getSourceImage().getImage(level);
    }

    @Override
    protected RenderedImage createImage(final int level) {
        final List<RenderedImage> tileImages = Collections.synchronizedList(new ArrayList<>());
        PlanarImage opImage;
        try {
            opImage = createTileImage(level);
            if (opImage != null) {
                opImage = TranslateDescriptor.create(opImage,
                        0.0f,
                        0.0f,
                        Interpolation.getInstance(Interpolation.INTERP_NEAREST),
                        null);
            }
        } catch (IOException ex) {
            logger.log(Level.WARNING, ex.getMessage(), ex);
            opImage = ConstantDescriptor.create((float) tileWidth, (float) tileHeight, new Number[]{0}, null);
        }
        tileImages.add(opImage);
        if (tileImages.isEmpty()) {
            logger.warning("No tile images for mosaic");
            return null;
        }

        final ImageLayout imageLayout = new ImageLayout();
        imageLayout.setMinX(0);
        imageLayout.setMinY(0);
        imageLayout.setTileWidth(JAI.getDefaultTileSize().width);
        imageLayout.setTileHeight(JAI.getDefaultTileSize().height);
        imageLayout.setTileGridXOffset(0);
        imageLayout.setTileGridYOffset(0);

        RenderedOp mosaicOp = MosaicDescriptor.create(tileImages.toArray(new RenderedImage[tileImages.size()]),
                MosaicDescriptor.MOSAIC_TYPE_OVERLAY,
                null, null, null,  new double[] { Float.NaN },
                new RenderingHints(JAI.KEY_IMAGE_LAYOUT, imageLayout));
        int fittingRectWidth = scaleValue(imageWidth, level);
        int fittingRectHeight = scaleValue(imageHeight, level);

        Rectangle fitRect = new Rectangle(0, 0, fittingRectWidth, fittingRectHeight);
        final Rectangle destBounds = DefaultMultiLevelSource.getLevelImageBounds(fitRect, Math.pow(2.0, level));

        BorderExtender borderExtender = BorderExtender.createInstance(BorderExtender.BORDER_COPY);

        if (mosaicOp.getWidth() < destBounds.width || mosaicOp.getHeight() < destBounds.height) {
            int rightPad = destBounds.width - mosaicOp.getWidth();
            int bottomPad = destBounds.height - mosaicOp.getHeight();
            mosaicOp = BorderDescriptor.create(mosaicOp, 0, rightPad, 0, bottomPad, borderExtender, null);
        }

        return mosaicOp;
    }

    @Override
    public synchronized void reset() {
        super.reset();
        System.gc();
    }

    private int scaleValue(final int source, final int level) {
        int size = source >> level;
        final int sizeTest = size << level;
        if (sizeTest < source) {
            size++;
        }
        return size;
    }
}
