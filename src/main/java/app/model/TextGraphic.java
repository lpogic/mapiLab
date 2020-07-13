package app.model;

import app.model.variable.Fun;
import app.model.variable.Monitor;
import app.model.variable.Var;
import jorg.jorg.Jorg;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import suite.suite.Subject;
import suite.suite.Suite;
import suite.suite.util.Cascade;

import java.io.IOException;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.stb.STBTruetype.*;
import static org.lwjgl.stb.STBTruetype.stbtt_GetFontVMetrics;

public class TextGraphic {

    private static final Subject sized = Suite.set();

    public static TextGraphic getForSize(double size) {
        int s = ((int)Math.min(Math.ceil(Math.abs(size)), 191.) + 32) / 32;
        return sized.getDone(s, () -> TextGraphic.form(Suite.set("size", s * 32))).asExpected();
    }

    private static final Shader defaultShader = Jorg.withRecipe(Shader::form).read(Shader.class.getClassLoader().getResourceAsStream("jorg/textShader.jorg"));

    private final Subject chars = Suite.set();
    private final String fontPath;
    private final int vao;
    private final int vbo;
    private final STBTTFontinfo fontInfo;
    private final ByteBuffer trueType;
    private final int bitmapWidth;
    private final int bitmapHeight;
    private final float ascend;
    private final float descent;
    private final float lineGap;
    private final int size;

    private final Var<Shader> shader;
    private final Var<Integer> projectionWidth;
    private final Var<Integer> projectionHeight;
    private final Monitor projectionMonitor;

    public static TextGraphic form(Subject sub) {
        Shader shader = sub.get("shader").orGiven(defaultShader);
        String font = sub.get("font").orGiven("ttf/trebuc.ttf");
        int fontSize = Suite.from(sub).get("size").orGiven(24);

        return new TextGraphic(shader, font, fontSize, 800, 600);
    }

    public TextGraphic(Shader shader, String fontPath, int fontSize, int projectionWidth, int projectionHeight) {
        this.shader = Var.create(shader);
        this.fontPath = fontPath;
        this.size = fontSize;
        this.bitmapWidth = 1024;
        this.bitmapHeight = 512;
        this.projectionWidth = Var.create(projectionWidth);
        this.projectionHeight = Var.create(projectionHeight);
        this.projectionMonitor = Monitor.compose(true, Suite.set(this.projectionWidth).set(this.projectionHeight).set(this.shader));

        try {
            trueType = IOUtil.ioResourceToByteBuffer(fontPath, 512 * 1024);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        fontInfo = STBTTFontinfo.create();
        if (!stbtt_InitFont(fontInfo, trueType)) {
            throw new IllegalStateException("Failed to initialize font information.");
        }

        int[] ascend = new int[1], descent = new int[1], lineGap = new int[1];

        stbtt_GetFontVMetrics(fontInfo, ascend, descent, lineGap);

        this.ascend = ascend[0];
        this.descent = descent[0];
        this.lineGap = lineGap[0];

        bake(' ', '~', bitmapWidth, bitmapHeight);
        // Polskie krzaki
        bake(211, 211, bitmapWidth, bitmapHeight);
        bake(243, 243, bitmapWidth, bitmapHeight);
        bake(260, 263, bitmapWidth, bitmapHeight);
        bake(280, 281, bitmapWidth, bitmapHeight);
        bake(321, 324, bitmapWidth, bitmapHeight);
        bake(346, 347, bitmapWidth, bitmapHeight);
        bake(377, 380, bitmapWidth, bitmapHeight);

        vbo = glGenBuffers();
        vao = glGenVertexArrays();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, 96, GL_DYNAMIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 4, GL_FLOAT, false, 16, 0);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);

    }

    private void bake(int firstCodePoint, int lastCodepoint, int bitmapWidth, int bitmapHeight) {
        int textureID = glGenTextures();
        STBTTBakedChar.Buffer buffer = STBTTBakedChar.malloc(1 + lastCodepoint - firstCodePoint);

        ByteBuffer bitmap = BufferUtils.createByteBuffer(bitmapWidth * bitmapHeight);
        stbtt_BakeFontBitmap(trueType, size, bitmap, bitmapWidth, bitmapHeight, firstCodePoint, buffer);

        glBindTexture(GL_TEXTURE_2D, textureID);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RED, bitmapWidth, bitmapHeight, 0, GL_RED, GL_UNSIGNED_BYTE, bitmap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        for(int i = firstCodePoint;i <= lastCodepoint; ++i) {
            chars.set(i, new CharacterTexture(textureID, buffer, i - firstCodePoint));
        }

        glBindTexture(GL_TEXTURE_2D, 0);
    }

    public float getAscend() {
        return ascend;
    }

    public float getDescent() {
        return descent;
    }

    public float getLineGap() {
        return lineGap;
    }

    public int getSize() {
        return size;
    }

    public Var<Shader> getShader() {
        return shader;
    }

    public Var<Integer> getProjectionWidth() {
        return projectionWidth;
    }

    public Var<Integer> getProjectionHeight() {
        return projectionHeight;
    }

    public void render(String text, float x, float y, double scale, Vector3f color) {

        float[] X = new float[]{x};
        float[] Y = new float[]{y};
        Shader shader = this.shader.get();
        int width = projectionWidth.get();
        int height = projectionHeight.get();
        float s = (float)scale;

        shader.use();
        if(projectionMonitor.release()) {
            shader.set("projection", new Matrix4f().ortho2D(0f, width, 0f, height));
        }
        glActiveTexture(GL_TEXTURE0);
        shader.set("textColor", color.x, color.y, color.z);
        glBindVertexArray(vao);

        STBTTAlignedQuad quad = STBTTAlignedQuad.create();

        Cascade<Integer> codePoints = new Cascade<>(text.codePoints().iterator());

        for(int codePoint : codePoints.toEnd()) {
            if(chars.get(codePoint).desolated()) {
                bake(codePoint, codePoint, bitmapWidth, bitmapHeight);
            }
            CharacterTexture charTex = chars.get(codePoint).asExpected();
            float xRef = X[0];
            float yRef = Y[0];

            stbtt_GetBakedQuad(charTex.getBuffer(), bitmapWidth, bitmapHeight, charTex.getBufferOffset(),
                    X, Y, quad, true);

            float x0 = scale(quad.x0(), xRef, s);
            float x1 = scale(quad.x1(), xRef, s);
            float y0 = height - scale(quad.y0(), yRef, s);
            float y1 = height - scale(quad.y1(), yRef, s);
            X[0] = scale(X[0], xRef, s);

            float[] vertices = new float[] {
                    x0, y0, quad.s0(), quad.t0(),
                    x0, y1, quad.s0(), quad.t1(),
                    x1, y0, quad.s1(), quad.t0(),
                    x1, y0, quad.s1(), quad.t0(),
                    x0, y1, quad.s0(), quad.t1(),
                    x1, y1, quad.s1(), quad.t1(),
            };

            glBindTexture(GL_TEXTURE_2D, charTex.getTextureGlid());
            glBindBuffer(GL_ARRAY_BUFFER, vbo);
            glBufferSubData(GL_ARRAY_BUFFER, 0, vertices);
            glDrawArrays(GL_TRIANGLES, 0, 6);
            glBindBuffer(GL_ARRAY_BUFFER, 0);
        }
        glBindVertexArray(0);
        glBindTexture(GL_TEXTURE_2D, 0);
    }

    private float scale(float rel, float ref, float scale) {
        return (rel - ref) * scale + ref;
    }

//    private float getStringWidth(STBTTFontinfo info, String text, int fontHeight) {
//        int width = 0;
//        int lastCp = 0;
//        int[] advancedWidth = new int[1];
//        int[] leftSideBearing = new int[1];
//        Iterable<Integer> it = () -> text.chars().iterator();
//        for(int cp : it) {
//            stbtt_GetCodepointHMetrics(info, cp, advancedWidth, leftSideBearing);
//            width += advancedWidth[0];
//            if(isKerningEnabled()) {
//                if(lastCp != 0) {
//                    width += stbtt_GetCodepointKernAdvance(info, lastCp, cp);
//                }
//                lastCp = cp;
//            }
//        }
//
//        return width * stbtt_ScaleForPixelHeight(info, fontHeight);
//    }

}
