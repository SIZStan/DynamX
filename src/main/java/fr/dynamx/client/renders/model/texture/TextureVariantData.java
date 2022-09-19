package fr.dynamx.client.renders.model.texture;

import fr.dynamx.api.obj.IModelTextureVariantsSupplier;
import fr.dynamx.common.contentpack.type.PartWheelInfo;
import fr.dynamx.client.renders.model.renderer.ObjObjectRenderer;
import lombok.Getter;
import lombok.ToString;

import javax.annotation.Nullable;

/**
 * Properties of a texture used in obj models
 *
 * @see IModelTextureVariantsSupplier
 * @see fr.dynamx.common.contentpack.ModularVehicleInfo
 * @see PartWheelInfo
 * @see ObjObjectRenderer
 */
@ToString
public class TextureVariantData {
    /**
     * The name of the texture, matching with the name in mtl files
     */
    @Getter
    private final String name;
    /**
     * The id of this texture, unique in one vehicle
     */
    @Getter
    private final byte id;
    /**
     *  The associated item texture name, or null
     */
    @Getter
    @Nullable
    private final String iconName;

    /**
     * This texture will not have an associated item
     *
     * @param name The name of the texture, matching with the name in mtl files
     * @param id   The id of this texture, should be unique in one vehicle
     */
    public TextureVariantData(String name, byte id) {
        this(name, id, null);
    }

    /**
     * This texture will have an associated item
     *
     * @param name     The name of the texture, matching with the name in mtl files
     * @param id       The id of this texture, should be unique in one vehicle
     * @param iconName The name if the texture of the item
     */
    public TextureVariantData(String name, byte id, String iconName) {
        this.name = name;
        this.id = id;
        this.iconName = iconName;
    }

    /**
     * @return If this texture must have an associated item, used for multi-skins vehicles
     */
    public boolean isItem() {
        return iconName != null;
    }
}