package com.dynamo.cr.tileeditor.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.isNull;
import static org.mockito.Matchers.notNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Enumeration;

import org.eclipse.core.commands.operations.DefaultOperationHistory;
import org.eclipse.core.commands.operations.IOperationHistory;
import org.eclipse.core.commands.operations.IUndoContext;
import org.eclipse.core.commands.operations.UndoContext;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.osgi.util.NLS;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;

import com.dynamo.cr.editor.core.EditorUtil;
import com.dynamo.cr.properties.IPropertyModel;
import com.dynamo.cr.tileeditor.Activator;
import com.dynamo.cr.tileeditor.core.ITileSetView;
import com.dynamo.cr.tileeditor.core.TileSetModel;
import com.dynamo.cr.tileeditor.core.TileSetPresenter;
import com.dynamo.tile.proto.Tile.TileSet;
import com.google.protobuf.TextFormat;

public class TileSetTest {
    private IProject project;
    private IContainer contentRoot;
    private NullProgressMonitor monitor;
    ITileSetView view;
    TileSetModel model;
    TileSetPresenter presenter;
    IOperationHistory history;
    IUndoContext undoContext;
    IPropertyModel<TileSetModel, TileSetModel> propertyModel;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() throws Exception {
        project = ResourcesPlugin.getWorkspace().getRoot().getProject("test");
        monitor = new NullProgressMonitor();
        if (project.exists()) {
            project.delete(true, monitor);
        }
        project.create(monitor);
        project.open(monitor);

        IProjectDescription pd = project.getDescription();
        pd.setNatureIds(new String[] { "com.dynamo.cr.editor.core.crnature" });
        project.setDescription(pd, monitor);

        Bundle bundle = Platform.getBundle("com.dynamo.cr.tileeditor.test");
        Enumeration<URL> entries = bundle.findEntries("/test", "*", true);
        while (entries.hasMoreElements()) {
            URL url = entries.nextElement();
            IPath path = new Path(url.getPath()).removeFirstSegments(1);
            // Create path of url-path and remove first element, ie /test/sounds/ -> /sounds
            if (url.getFile().endsWith("/")) {
                project.getFolder(path).create(true, true, monitor);
            } else {
                InputStream is = url.openStream();
                IFile file = project.getFile(path);
                file.create(is, true, monitor);
                is.close();
            }
        }
        this.contentRoot = EditorUtil.findContentRoot(this.project.getFile("game.project"));

        System.setProperty("java.awt.headless", "true");
        this.view = mock(ITileSetView.class);
        this.history = new DefaultOperationHistory();
        this.undoContext = new UndoContext();
        this.model = new TileSetModel(this.contentRoot, this.history, this.undoContext);
        this.presenter = new TileSetPresenter(this.model, this.view);
        this.propertyModel = (IPropertyModel<TileSetModel, TileSetModel>) this.model.getAdapter(IPropertyModel.class);
    }

    @After
    public void teardown() {
        this.presenter.dispose();
    }

    private TileSet loadEmptyFile() throws IOException {
        // new file
        TileSet tileSet = TileSet.newBuilder()
                .setImage("")
                .setTileWidth(16)
                .setTileHeight(16)
                .setTileSpacing(0)
                .setTileMargin(0)
                .setCollision("")
                .setMaterialTag("tile")
                .addCollisionGroups("default")
                .build();
        this.presenter.load(tileSet);
        return tileSet;
    }

    /**
     * Test load
     * @throws IOException
     */
    @Test
    public void testLoad() throws IOException {
        TileSet tileSet = loadEmptyFile();

        assertEquals(tileSet.getImage(), this.model.getImage());
        assertEquals(tileSet.getTileWidth(), this.model.getTileWidth());
        assertEquals(tileSet.getTileHeight(), this.model.getTileHeight());
        assertEquals(tileSet.getTileMargin(), this.model.getTileMargin());
        assertEquals(tileSet.getTileSpacing(), this.model.getTileSpacing());
        assertEquals(tileSet.getCollision(), this.model.getCollision());
        assertEquals(tileSet.getMaterialTag(), this.model.getMaterialTag());
        assertEquals(1, this.model.getCollisionGroups().size());
        assertEquals(tileSet.getCollisionGroups(0), this.model.getCollisionGroups().get(0));
        assertEquals(0, this.model.getConvexHulls().size());
        assertEquals(null, this.model.getConvexHullPoints());

        verify(this.view, times(1)).setImage((BufferedImage)isNull());
        verify(this.view, times(1)).setTileWidth(eq(16));
        verify(this.view, times(1)).setTileHeight(eq(16));
        verify(this.view, times(1)).setTileMargin(eq(0));
        verify(this.view, times(1)).setTileSpacing(eq(0));
        verify(this.view, times(1)).setCollision((BufferedImage)isNull());
        verify(this.view, times(1)).refreshProperties();
        verify(this.view, times(1)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, never()).setHulls(any(float[].class), any(int[].class), any(int[].class), any(Color[].class));
        verify(this.view, never()).setHullColor(anyInt(), any(Color.class));
        verify(this.view, never()).setDirty(anyBoolean());
    }

    /**
     * Use Case 1.1.1 - Create the Tile Set
     * 
     * @throws IOException
     */
    @Test
    public void testUseCase111() throws Exception {
        TileSet emptyTileSet = loadEmptyFile();

        String tileSetFile = "/mario_tileset.png";

        // image

        assertEquals(emptyTileSet.getImage(), this.model.getImage());
        verify(this.view, times(1)).setImage((BufferedImage)isNull());
        verify(this.view, times(1)).refreshProperties();
        verify(this.view, never()).setDirty(true);
        this.model.executeOperation(propertyModel.setPropertyValue("image", tileSetFile));
        assertEquals(tileSetFile, this.model.getImage());
        verify(this.view, times(1)).setImage((BufferedImage)notNull());
        verify(this.view, times(3)).refreshProperties();
        verify(this.view, times(1)).setDirty(true);
        this.history.undo(this.undoContext, null, null);
        assertEquals(emptyTileSet.getImage(), this.model.getImage());
        verify(this.view, times(2)).setImage((BufferedImage)isNull());
        verify(this.view, times(5)).refreshProperties();
        verify(this.view, times(1)).setDirty(false);
        this.history.redo(this.undoContext, null, null);
        assertEquals(tileSetFile, this.model.getImage());
        verify(this.view, times(2)).setImage((BufferedImage)notNull());
        verify(this.view, times(7)).refreshProperties();
        verify(this.view, times(2)).setDirty(true);

        // tile width

        assertEquals(emptyTileSet.getTileWidth(), this.model.getTileWidth());
        verify(this.view, times(1)).setTileWidth(eq(16));
        verify(this.view, times(7)).refreshProperties();
        this.model.executeOperation(propertyModel.setPropertyValue("tileWidth", 17));
        assertEquals(17, this.model.getTileWidth());
        verify(this.view, times(1)).setTileWidth(eq(17));
        verify(this.view, times(8)).refreshProperties();
        this.history.undo(this.undoContext, null, null);
        assertEquals(emptyTileSet.getTileWidth(), this.model.getTileWidth());
        verify(this.view, times(2)).setTileWidth(eq(16));
        verify(this.view, times(9)).refreshProperties();
        this.history.redo(this.undoContext, null, null);
        assertEquals(17, this.model.getTileWidth());
        verify(this.view, times(2)).setTileWidth(eq(17));
        verify(this.view, times(10)).refreshProperties();
        this.history.undo(this.undoContext, null, null);

        // tile height

        assertEquals(emptyTileSet.getTileHeight(), this.model.getTileHeight());
        verify(this.view, times(1)).setTileHeight(eq(16));
        verify(this.view, times(11)).refreshProperties();
        this.model.executeOperation(propertyModel.setPropertyValue("tileHeight", 17));
        assertEquals(17, this.model.getTileHeight());
        verify(this.view, times(1)).setTileHeight(eq(17));
        verify(this.view, times(12)).refreshProperties();
        this.history.undo(this.undoContext, null, null);
        assertEquals(emptyTileSet.getTileHeight(), this.model.getTileHeight());
        verify(this.view, times(2)).setTileHeight(eq(16));
        verify(this.view, times(13)).refreshProperties();
        this.history.redo(this.undoContext, null, null);
        assertEquals(17, this.model.getTileHeight());
        verify(this.view, times(2)).setTileHeight(eq(17));
        verify(this.view, times(14)).refreshProperties();
        this.history.undo(this.undoContext, null, null);

        // tile margin

        assertEquals(emptyTileSet.getTileMargin(), this.model.getTileMargin());
        verify(this.view, times(1)).setTileMargin(eq(0));
        verify(this.view, times(15)).refreshProperties();
        this.model.executeOperation(propertyModel.setPropertyValue("tileMargin", 1));
        assertEquals(1, this.model.getTileMargin());
        verify(this.view, times(1)).setTileMargin(eq(1));
        verify(this.view, times(16)).refreshProperties();
        this.history.undo(this.undoContext, null, null);
        assertEquals(0, this.model.getTileMargin());
        verify(this.view, times(2)).setTileMargin(eq(0));
        verify(this.view, times(17)).refreshProperties();
        this.history.redo(this.undoContext, null, null);
        assertEquals(1, this.model.getTileMargin());
        verify(this.view, times(2)).setTileMargin(eq(1));
        verify(this.view, times(18)).refreshProperties();

        // reset since we don't want any tile margin
        this.history.undo(this.undoContext, null, null);

        // tile spacing

        assertEquals(emptyTileSet.getTileSpacing(), this.model.getTileSpacing());
        verify(this.view, times(1)).setTileSpacing(eq(0));
        verify(this.view, times(19)).refreshProperties();
        this.model.executeOperation(propertyModel.setPropertyValue("tileSpacing", 1));
        assertEquals(1, this.model.getTileSpacing());
        verify(this.view, times(1)).setTileSpacing(eq(1));
        verify(this.view, times(20)).refreshProperties();
        this.history.undo(this.undoContext, null, null);
        assertEquals(0, this.model.getTileSpacing());
        verify(this.view, times(2)).setTileSpacing(eq(0));
        verify(this.view, times(21)).refreshProperties();
        this.history.redo(this.undoContext, null, null);
        assertEquals(1, this.model.getTileSpacing());
        verify(this.view, times(2)).setTileSpacing(eq(1));
        verify(this.view, times(22)).refreshProperties();

        // collision

        assertEquals(0, this.model.getConvexHulls().size());
        assertEquals(emptyTileSet.getCollision(), this.model.getCollision());
        assertEquals(null, this.model.getConvexHullPoints());
        verify(this.view, times(1)).setCollision((BufferedImage)isNull());
        verify(this.view, times(22)).refreshProperties();
        verify(this.view, never()).setHulls(any(float[].class), any(int[].class), any(int[].class), any(Color[].class));
        this.model.executeOperation(propertyModel.setPropertyValue("collision", tileSetFile));
        assertEquals(tileSetFile, this.model.getCollision());
        verify(this.view, times(1)).setCollision((BufferedImage)notNull());
        verify(this.view, times(23)).refreshProperties();
        verify(this.view, times(1)).setHulls(any(float[].class), any(int[].class), any(int[].class), any(Color[].class));
        this.history.undo(this.undoContext, null, null);
        assertEquals(emptyTileSet.getCollision(), this.model.getCollision());
        verify(this.view, times(2)).setCollision((BufferedImage)isNull());
        verify(this.view, times(24)).refreshProperties();
        verify(this.view, times(1)).setHulls(any(float[].class), any(int[].class), any(int[].class), any(Color[].class));
        this.history.redo(this.undoContext, null, null);
        assertEquals(tileSetFile, this.model.getCollision());
        verify(this.view, times(2)).setCollision((BufferedImage)notNull());
        verify(this.view, times(25)).refreshProperties();
        verify(this.view, times(2)).setHulls(any(float[].class), any(int[].class), any(int[].class), any(Color[].class));

        for (int i = 0; i < 10; ++i) {
            assertEquals(8, this.model.getConvexHulls().get(i).getCount());
        }
        for (int i = 10; i < 14; ++i) {
            assertEquals(4, this.model.getConvexHulls().get(i).getCount());
        }
        for (int i = 14; i < 20; ++i) {
            assertEquals(8, this.model.getConvexHulls().get(i).getCount());
        }

        // material tag

        assertEquals(emptyTileSet.getMaterialTag(), this.model.getMaterialTag());
        verify(this.view, times(25)).refreshProperties();
        this.model.executeOperation(propertyModel.setPropertyValue("materialTag", "my_material"));
        assertEquals("my_material", this.model.getMaterialTag());
        verify(this.view, times(26)).refreshProperties();
        this.history.undo(this.undoContext, null, null);
        assertEquals(emptyTileSet.getMaterialTag(), this.model.getMaterialTag());
        verify(this.view, times(27)).refreshProperties();
        this.history.redo(this.undoContext, null, null);
        assertEquals("my_material", this.model.getMaterialTag());
        verify(this.view, times(28)).refreshProperties();

        // reset since we don't want to edit material tag
        this.history.undo(this.undoContext, null, null);

        verify(this.view, never()).setHullColor(anyInt(), any(Color.class));
    }

    /**
     * Use Case 1.1.4 - Add a Collision Group
     * 
     * @throws IOException
     */
    @Test
    public void testUseCase114() throws Exception {

        // requirement
        testUseCase111();

        // preconditions
        assertEquals(1, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals(0, this.model.getSelectedCollisionGroups().length);
        verify(this.view, times(1)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));

        // add the group
        this.presenter.addCollisionGroup("hazad");
        assertEquals(2, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("hazad", this.model.getCollisionGroups().get(1));
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("hazad", this.model.getSelectedCollisionGroups()[0]);
        verify(this.view, times(2)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        this.history.undo(this.undoContext, null, null);
        assertEquals(1, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals(0, this.model.getSelectedCollisionGroups().length);
        verify(this.view, times(3)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        this.history.redo(this.undoContext, null, null);
        assertEquals(2, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("hazad", this.model.getCollisionGroups().get(1));
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("hazad", this.model.getSelectedCollisionGroups()[0]);
        verify(this.view, times(4)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));

        // preconditions
        assertEquals("", this.model.getConvexHulls().get(1).getCollisionGroup());
        assertEquals("", this.model.getConvexHulls().get(2).getCollisionGroup());
        verify(this.view, never()).setHullColor(eq(1), any(Color.class));
        verify(this.view, never()).setHullColor(eq(2), any(Color.class));

        // simulate painting
        this.presenter.beginSetConvexHullCollisionGroup("hazad");
        this.presenter.setConvexHullCollisionGroup(1);
        this.presenter.setConvexHullCollisionGroup(1);
        this.presenter.setConvexHullCollisionGroup(2);
        this.presenter.setConvexHullCollisionGroup(2);
        this.presenter.endSetConvexHullCollisionGroup();
        assertEquals("hazad", this.model.getConvexHulls().get(1).getCollisionGroup());
        assertEquals("hazad", this.model.getConvexHulls().get(2).getCollisionGroup());
        verify(this.view, times(1)).setHullColor(eq(1), any(Color.class));
        verify(this.view, times(1)).setHullColor(eq(2), any(Color.class));
        this.history.undo(this.undoContext, null, null);
        assertEquals("", this.model.getConvexHulls().get(1).getCollisionGroup());
        assertEquals("", this.model.getConvexHulls().get(2).getCollisionGroup());
        verify(this.view, times(2)).setHullColor(eq(1), any(Color.class));
        verify(this.view, times(2)).setHullColor(eq(2), any(Color.class));
        this.history.redo(this.undoContext, null, null);
        assertEquals("hazad", this.model.getConvexHulls().get(1).getCollisionGroup());
        assertEquals("hazad", this.model.getConvexHulls().get(2).getCollisionGroup());
        verify(this.view, times(3)).setHullColor(eq(1), any(Color.class));
        verify(this.view, times(3)).setHullColor(eq(2), any(Color.class));
    }

    /**
     * Use Case 1.1.5 - Rename Collision Group
     * 
     * @throws IOException
     */
    @Test
    public void testUseCase115() throws Exception {

        // requirement
        testUseCase114();

        // preconditions
        assertEquals("hazad", this.model.getCollisionGroups().get(1));
        assertEquals("hazad", this.model.getConvexHulls().get(1).getCollisionGroup());
        verify(this.view, times(4)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(3)).setHullColor(eq(1), any(Color.class));

        this.presenter.selectCollisionGroups(new String[] {"hazad"});
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("hazad", this.model.getSelectedCollisionGroups()[0]);

        // test
        this.presenter.renameSelectedCollisionGroups(new String[] {"hazard"});
        assertEquals("hazard", this.model.getCollisionGroups().get(1));
        assertEquals("hazard", this.model.getConvexHulls().get(1).getCollisionGroup());
        verify(this.view, times(5)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(4)).setHullColor(eq(1), any(Color.class));
        this.history.undo(this.undoContext, null, null);
        assertEquals("hazad", this.model.getCollisionGroups().get(1));
        assertEquals("hazad", this.model.getConvexHulls().get(1).getCollisionGroup());
        verify(this.view, times(6)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(5)).setHullColor(eq(1), any(Color.class));
        this.history.redo(this.undoContext, null, null);
        assertEquals("hazard", this.model.getCollisionGroups().get(1));
        assertEquals("hazard", this.model.getConvexHulls().get(1).getCollisionGroup());
        verify(this.view, times(7)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(6)).setHullColor(eq(1), any(Color.class));
    }

    /**
     * Use Case 1.1.6 - Rename Collision Group to Existing Group
     * 
     * @throws IOException
     */
    @Test
    public void testUseCase116() throws Exception {

        // requirements
        testUseCase111();

        // add group
        this.presenter.addCollisionGroup("obstruction");

        // paint
        this.presenter.beginSetConvexHullCollisionGroup("obstruction");
        this.presenter.setConvexHullCollisionGroup(2);
        this.presenter.endSetConvexHullCollisionGroup();

        // add another group
        this.presenter.addCollisionGroup("obstuction");

        // paint
        this.presenter.beginSetConvexHullCollisionGroup("obstuction");
        this.presenter.setConvexHullCollisionGroup(3);
        this.presenter.endSetConvexHullCollisionGroup();

        // preconditions
        assertEquals(3, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("obstruction", this.model.getCollisionGroups().get(1));
        assertEquals("obstuction", this.model.getCollisionGroups().get(2));
        assertEquals("obstruction", this.model.getConvexHulls().get(2).getCollisionGroup());
        assertEquals("obstuction", this.model.getConvexHulls().get(3).getCollisionGroup());
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("obstuction", this.model.getSelectedCollisionGroups()[0]);
        verify(this.view, times(3)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(1)).setHullColor(eq(2), any(Color.class));
        verify(this.view, times(1)).setHullColor(eq(3), any(Color.class));

        // test
        this.presenter.renameSelectedCollisionGroups(new String[] {"obstruction"});
        assertEquals(2, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("obstruction", this.model.getCollisionGroups().get(1));
        assertEquals("obstruction", this.model.getConvexHulls().get(2).getCollisionGroup());
        assertEquals("obstruction", this.model.getConvexHulls().get(3).getCollisionGroup());
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("obstruction", this.model.getSelectedCollisionGroups()[0]);
        verify(this.view, times(4)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(2)).setHullColor(eq(3), any(Color.class));

        // undo
        this.history.undo(this.undoContext, null, null);
        assertEquals(3, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("obstruction", this.model.getCollisionGroups().get(1));
        assertEquals("obstuction", this.model.getCollisionGroups().get(2));
        assertEquals("obstruction", this.model.getConvexHulls().get(2).getCollisionGroup());
        assertEquals("obstuction", this.model.getConvexHulls().get(3).getCollisionGroup());
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("obstuction", this.model.getSelectedCollisionGroups()[0]);
        verify(this.view, times(5)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(3)).setHullColor(eq(3), any(Color.class));

        // redo
        this.history.redo(this.undoContext, null, null);
        assertEquals(2, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("obstruction", this.model.getCollisionGroups().get(1));
        assertEquals("obstruction", this.model.getConvexHulls().get(2).getCollisionGroup());
        assertEquals("obstruction", this.model.getConvexHulls().get(3).getCollisionGroup());
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("obstruction", this.model.getSelectedCollisionGroups()[0]);
        verify(this.view, times(6)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(4)).setHullColor(eq(3), any(Color.class));
    }

    /**
     * Use Case 1.1.7 - Rename Collision Groups to Single Group
     * 
     * @throws IOException
     */
    @Test
    public void testUseCase117() throws Exception {

        // requirements
        testUseCase111();

        // add group
        this.presenter.addCollisionGroup("obstruction");

        // paint
        this.presenter.beginSetConvexHullCollisionGroup("obstruction");
        this.presenter.setConvexHullCollisionGroup(2);
        this.presenter.endSetConvexHullCollisionGroup();

        // add another group
        this.presenter.addCollisionGroup("obstuction");

        // paint
        this.presenter.beginSetConvexHullCollisionGroup("obstuction");
        this.presenter.setConvexHullCollisionGroup(3);
        this.presenter.endSetConvexHullCollisionGroup();

        this.presenter.selectCollisionGroups(new String[] {"obstuction", "obstruction"});

        // preconditions
        assertEquals(3, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("obstruction", this.model.getCollisionGroups().get(1));
        assertEquals("obstuction", this.model.getCollisionGroups().get(2));
        assertEquals("obstruction", this.model.getConvexHulls().get(2).getCollisionGroup());
        assertEquals("obstuction", this.model.getConvexHulls().get(3).getCollisionGroup());
        assertEquals(2, this.model.getSelectedCollisionGroups().length);
        assertEquals("obstuction", this.model.getSelectedCollisionGroups()[0]);
        assertEquals("obstruction", this.model.getSelectedCollisionGroups()[1]);
        verify(this.view, times(3)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(1)).setHullColor(eq(2), any(Color.class));
        verify(this.view, times(1)).setHullColor(eq(3), any(Color.class));

        // test
        this.presenter.renameSelectedCollisionGroups(new String[] {"obstruction2", "obstruction2"});
        assertEquals(2, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("obstruction2", this.model.getCollisionGroups().get(1));
        assertEquals("obstruction2", this.model.getConvexHulls().get(2).getCollisionGroup());
        assertEquals("obstruction2", this.model.getConvexHulls().get(3).getCollisionGroup());
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("obstruction2", this.model.getSelectedCollisionGroups()[0]);
        verify(this.view, times(4)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(2)).setHullColor(eq(3), any(Color.class));

        // undo
        this.history.undo(this.undoContext, null, null);
        assertEquals(3, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("obstruction", this.model.getCollisionGroups().get(1));
        assertEquals("obstuction", this.model.getCollisionGroups().get(2));
        assertEquals("obstruction", this.model.getConvexHulls().get(2).getCollisionGroup());
        assertEquals("obstuction", this.model.getConvexHulls().get(3).getCollisionGroup());
        assertEquals(2, this.model.getSelectedCollisionGroups().length);
        assertEquals("obstuction", this.model.getSelectedCollisionGroups()[0]);
        assertEquals("obstruction", this.model.getSelectedCollisionGroups()[1]);
        verify(this.view, times(6)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(3)).setHullColor(eq(3), any(Color.class));

        // redo
        this.history.redo(this.undoContext, null, null);
        assertEquals(2, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("obstruction2", this.model.getCollisionGroups().get(1));
        assertEquals("obstruction2", this.model.getConvexHulls().get(2).getCollisionGroup());
        assertEquals("obstruction2", this.model.getConvexHulls().get(3).getCollisionGroup());
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("obstruction2", this.model.getSelectedCollisionGroups()[0]);
        verify(this.view, times(7)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(4)).setHullColor(eq(3), any(Color.class));
    }

    /**
     * Use Case 1.1.9 - Remove Collision Group
     * 
     * @throws IOException
     */
    @Test
    public void testUseCase119() throws Exception {

        // requirements
        testUseCase114();

        // add group, to test group order
        this.presenter.addCollisionGroup("obstruction");

        // preconditions
        assertEquals(3, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("hazad", this.model.getCollisionGroups().get(1));
        assertEquals("obstruction", this.model.getCollisionGroups().get(2));
        assertEquals("hazad", this.model.getConvexHulls().get(1).getCollisionGroup());
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("obstruction", this.model.getSelectedCollisionGroups()[0]);
        verify(this.view, times(5)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(3)).setHullColor(eq(1), any(Color.class));

        this.presenter.selectCollisionGroups(new String[] {"hazad"});
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("hazad", this.model.getSelectedCollisionGroups()[0]);
        this.presenter.removeSelectedCollisionGroups();
        assertEquals(2, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("obstruction", this.model.getCollisionGroups().get(1));
        assertEquals("", this.model.getConvexHulls().get(1).getCollisionGroup());
        assertEquals(0, this.model.getSelectedCollisionGroups().length);
        verify(this.view, times(6)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(4)).setHullColor(eq(1), any(Color.class));

        this.history.undo(this.undoContext, null, null);
        assertEquals(3, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("hazad", this.model.getCollisionGroups().get(1));
        assertEquals("obstruction", this.model.getCollisionGroups().get(2));
        assertEquals("hazad", this.model.getConvexHulls().get(1).getCollisionGroup());
        assertEquals(1, this.model.getSelectedCollisionGroups().length);
        assertEquals("hazad", this.model.getSelectedCollisionGroups()[0]);
        verify(this.view, times(7)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(5)).setHullColor(eq(1), any(Color.class));

        this.history.redo(this.undoContext, null, null);
        assertEquals(2, this.model.getCollisionGroups().size());
        assertEquals("default", this.model.getCollisionGroups().get(0));
        assertEquals("obstruction", this.model.getCollisionGroups().get(1));
        assertEquals("", this.model.getConvexHulls().get(1).getCollisionGroup());
        assertEquals(0, this.model.getSelectedCollisionGroups().length);
        verify(this.view, times(8)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(6)).setHullColor(eq(1), any(Color.class));
    }

    /**
     * Use Case 1.1.8 - Save
     * 
     * @throws IOException
     */
    @Test
    public void testUseCase118() throws Exception {

        // requires
        testUseCase114();

        // preconditions
        verify(this.view, times(1)).setDirty(false);

        String newTileSetPath = "test/tmp.tileset";
        File newTileSetFile = new File(newTileSetPath);

        FileOutputStream outputStream = new FileOutputStream(newTileSetFile);
        this.presenter.save(outputStream, new NullProgressMonitor());
        TileSet.Builder tileSetBuilder = TileSet.newBuilder();
        TextFormat.merge(new InputStreamReader(new FileInputStream(newTileSetPath)), tileSetBuilder);
        TileSet tileSet = tileSetBuilder.build();
        newTileSetFile = new File(newTileSetPath);
        newTileSetFile.delete();
        TileSetModel newModel = new TileSetModel(this.contentRoot, this.history, this.undoContext);
        newModel.load(tileSet);
        assertEquals(this.model.getImage(), newModel.getImage());
        assertEquals(this.model.getTileWidth(), newModel.getTileWidth());
        assertEquals(this.model.getTileHeight(), newModel.getTileHeight());
        assertEquals(this.model.getTileMargin(), newModel.getTileMargin());
        assertEquals(this.model.getTileSpacing(), newModel.getTileSpacing());
        assertEquals(this.model.getCollision(), newModel.getCollision());
        assertEquals(this.model.getMaterialTag(), newModel.getMaterialTag());
        assertEquals(this.model.getConvexHulls(), newModel.getConvexHulls());
        assertEquals(this.model.getConvexHullPoints().length, newModel.getConvexHullPoints().length);
        for (int i = 0; i < this.model.getConvexHullPoints().length; ++i) {
            assertEquals(this.model.getConvexHullPoints()[i], newModel.getConvexHullPoints()[i], 0.000001);
        }
        assertEquals(this.model.getCollisionGroups(), newModel.getCollisionGroups());
        verify(this.view, times(2)).setDirty(false);
    }

    /**
     * Message 1.1 - Image not specified
     * @throws IOException
     */
    @Test
    public void testMessage11() throws IOException {
        loadEmptyFile();

        int code = Activator.STATUS_TS_IMG_NOT_SPECIFIED;

        assertTrue(this.model.hasPropertyStatus("image", code));
        assertEquals(Activator.getStatusMessage(code), this.model.getPropertyStatus("image", code).getMessage());
        verify(this.view, times(1)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("image", "/mario_tileset.png"));
        assertTrue(!this.model.hasPropertyStatus("image", Activator.STATUS_TS_IMG_NOT_SPECIFIED));
        verify(this.view, times(3)).refreshProperties();
    }

    /**
     * Message 1.2- Image not found
     * @throws IOException
     */
    @Test
    public void testMessage12() throws IOException {
        loadEmptyFile();

        int code = Activator.STATUS_TS_IMG_NOT_FOUND;

        assertTrue(!this.model.hasPropertyStatus("image", code));
        verify(this.view, times(1)).refreshProperties();

        String invalidPath = "/test";
        this.model.executeOperation(propertyModel.setPropertyValue("image", invalidPath));
        assertTrue(this.model.hasPropertyStatus("image", code));
        assertEquals(NLS.bind(Activator.getStatusMessage(code), invalidPath), this.model.getPropertyStatus("image", code).getMessage());
        verify(this.view, times(4)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("image", "/mario_tileset.png"));
        assertTrue(!this.model.hasPropertyStatus("image", code));
        verify(this.view, times(6)).refreshProperties();
    }

    /**
     * Message 1.3 - Collision image not found
     * @throws IOException
     */
    @Test
    public void testMessage13() throws IOException {
        loadEmptyFile();

        int code = Activator.STATUS_TS_COL_IMG_NOT_FOUND;

        assertTrue(!this.model.hasPropertyStatus("collision", code));
        verify(this.view, times(1)).refreshProperties();

        String invalidPath = "test";
        this.model.executeOperation(propertyModel.setPropertyValue("collision", invalidPath));
        assertTrue(this.model.hasPropertyStatus("collision", code));
        assertEquals(NLS.bind(Activator.getStatusMessage(code), invalidPath), this.model.getPropertyStatus("collision", code).getMessage());
        verify(this.view, times(3)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("collision", "/mario_tileset.png"));
        assertTrue(!this.model.hasPropertyStatus("collision", code));
        verify(this.view, times(5)).refreshProperties();
    }

    /**
     * Message 1.4 - Image and collision image have different dimensions
     * @throws IOException
     */
    @Test
    public void testMessage14() throws IOException {
        loadEmptyFile();

        int code = Activator.STATUS_TS_DIFF_IMG_DIMS;

        assertTrue(!this.model.hasPropertyStatus("image", code));
        assertTrue(!this.model.hasPropertyStatus("collision", code));
        verify(this.view, times(1)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("image", "/mario_tileset.png"));
        this.model.executeOperation(propertyModel.setPropertyValue("collision", "/mario_half_tileset.png"));
        assertTrue(this.model.hasPropertyStatus("image", code));
        String message = NLS.bind(Activator.getStatusMessage(code), new Object[] {84, 67, 84, 33});
        assertEquals(message, this.model.getPropertyStatus("image", code).getMessage());
        assertTrue(this.model.hasPropertyStatus("collision", code));
        assertEquals(message, this.model.getPropertyStatus("collision", code).getMessage());
        verify(this.view, times(6)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("collision", "/mario_tileset.png"));
        assertTrue(!this.model.hasPropertyStatus("image", code));
        assertTrue(!this.model.hasPropertyStatus("collision", code));
        verify(this.view, times(9)).refreshProperties();
    }

    /**
     * Message 1.5 - Invalid tile width
     * @throws IOException
     */
    @Test
    public void testMessage15() throws IOException {
        loadEmptyFile();

        int code = Activator.STATUS_TS_INVALID_TILE_WIDTH;

        assertTrue(!this.model.hasPropertyStatus("tileWidth", code));
        verify(this.view, times(1)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("tileWidth", 0));
        assertTrue(this.model.hasPropertyStatus("tileWidth", code));
        assertEquals(Activator.getStatusMessage(code), this.model.getPropertyStatus("tileWidth", code).getMessage());
        verify(this.view, times(3)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("tileWidth", 16));
        assertTrue(!this.model.hasPropertyStatus("tileWidth", code));
        verify(this.view, times(5)).refreshProperties();
    }

    /**
     * Message 1.6 - Invalid tile height
     * @throws IOException
     */
    @Test
    public void testMessage16() throws IOException {
        loadEmptyFile();

        int code = Activator.STATUS_TS_INVALID_TILE_HEIGHT;

        assertTrue(!this.model.hasPropertyStatus("tileHeight", code));
        verify(this.view, times(1)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("tileHeight", 0));
        assertTrue(this.model.hasPropertyStatus("tileHeight", code));
        assertEquals(Activator.getStatusMessage(code), this.model.getPropertyStatus("tileHeight", code).getMessage());
        verify(this.view, times(3)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("tileHeight", 16));
        assertTrue(!this.model.hasPropertyStatus("tileHeight", code));
        verify(this.view, times(5)).refreshProperties();
    }

    /**
     * Message 1.7 - Total tile width is greater than image width
     * @throws IOException
     */
    @Test
    public void testMessage17() throws IOException {
        loadEmptyFile();

        int code = Activator.STATUS_TS_TILE_WIDTH_GT_IMG;

        verify(this.view, times(1)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("image", "/mario_tileset.png"));

        assertTrue(!this.model.hasPropertyStatus("image", code));
        assertTrue(!this.model.hasPropertyStatus("tileWidth", code));
        verify(this.view, times(3)).refreshProperties();

        String message = NLS.bind(Activator.getStatusMessage(code), new Object[] {86, 84});
        this.model.executeOperation(propertyModel.setPropertyValue("tileWidth", 85));
        this.model.executeOperation(propertyModel.setPropertyValue("tileMargin", 1));
        assertTrue(this.model.hasPropertyStatus("image", code));
        assertEquals(message, this.model.getPropertyStatus("image", code).getMessage());
        assertTrue(this.model.hasPropertyStatus("tileWidth", code));
        assertEquals(message, this.model.getPropertyStatus("tileWidth", code).getMessage());
        assertTrue(this.model.hasPropertyStatus("tileMargin", code));
        assertEquals(message, this.model.getPropertyStatus("tileMargin", code).getMessage());
        verify(this.view, times(10)).refreshProperties();

        message = NLS.bind(Activator.getStatusMessage(code), new Object[] {85, 84});
        this.model.executeOperation(propertyModel.setPropertyValue("tileMargin", 0));
        assertTrue(this.model.hasPropertyStatus("image", code));
        assertEquals(message, this.model.getPropertyStatus("image", code).getMessage());
        assertTrue(this.model.hasPropertyStatus("tileWidth", code));
        assertEquals(message, this.model.getPropertyStatus("tileWidth", code).getMessage());
        assertTrue(!this.model.hasPropertyStatus("tileMargin", code));
        verify(this.view, times(14)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("tileWidth", 16));
        assertTrue(!this.model.hasPropertyStatus("image", code));
        assertTrue(!this.model.hasPropertyStatus("tileWidth", code));
        assertTrue(!this.model.hasPropertyStatus("tileMargin", code));
        verify(this.view, times(17)).refreshProperties();
    }

    /**
     * Message 1.8 - Total tile height is greater than image height
     * @throws IOException
     */
    @Test
    public void testMessage18() throws IOException {
        loadEmptyFile();

        int code = Activator.STATUS_TS_TILE_HEIGHT_GT_IMG;

        verify(this.view, times(1)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("image", "/mario_tileset.png"));

        assertTrue(!this.model.hasPropertyStatus("tileHeight", code));
        assertTrue(!this.model.hasPropertyStatus("tileWidth", code));
        verify(this.view, times(3)).refreshProperties();

        String message = NLS.bind(Activator.getStatusMessage(code), new Object[] {69, 67});
        this.model.executeOperation(propertyModel.setPropertyValue("tileHeight", 68));
        this.model.executeOperation(propertyModel.setPropertyValue("tileMargin", 1));
        assertTrue(this.model.hasPropertyStatus("image", code));
        assertEquals(message, this.model.getPropertyStatus("image", code).getMessage());
        assertTrue(this.model.hasPropertyStatus("tileHeight", code));
        assertEquals(message, this.model.getPropertyStatus("tileHeight", code).getMessage());
        assertTrue(this.model.hasPropertyStatus("tileMargin", code));
        assertEquals(message, this.model.getPropertyStatus("tileMargin", code).getMessage());
        verify(this.view, times(10)).refreshProperties();

        message = NLS.bind(Activator.getStatusMessage(code), new Object[] {68, 67});
        this.model.executeOperation(propertyModel.setPropertyValue("tileMargin", 0));
        assertTrue(this.model.hasPropertyStatus("image", code));
        assertEquals(message, this.model.getPropertyStatus("image", code).getMessage());
        assertTrue(this.model.hasPropertyStatus("tileHeight", code));
        assertEquals(message, this.model.getPropertyStatus("tileHeight", code).getMessage());
        assertTrue(!this.model.hasPropertyStatus("tileMargin", code));
        verify(this.view, times(14)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("tileHeight", 16));
        assertTrue(!this.model.hasPropertyStatus("image", code));
        assertTrue(!this.model.hasPropertyStatus("tileHeight", code));
        assertTrue(!this.model.hasPropertyStatus("tileMargin", code));
        verify(this.view, times(17)).refreshProperties();
    }

    /**
     * Message 1.9 - Empty material tag
     * @throws IOException
     */
    @Test
    public void testMessage19() throws IOException {
        loadEmptyFile();

        int code = Activator.STATUS_TS_MAT_NOT_SPECIFIED;

        assertTrue(!this.model.hasPropertyStatus("materialTag", code));
        verify(this.view, times(1)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("materialTag", ""));
        assertTrue(this.model.hasPropertyStatus("materialTag", code));
        assertEquals(Activator.getStatusMessage(code), this.model.getPropertyStatus("materialTag", code).getMessage());
        verify(this.view, times(3)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("materialTag", "tile"));
        assertTrue(!this.model.hasPropertyStatus("materialTag", code));
        verify(this.view, times(5)).refreshProperties();
    }

    /**
     * Message 1.10 - Invalid tile margin
     * @throws IOException
     */
    @Test
    public void testMessage110() throws IOException {
        loadEmptyFile();

        int code = Activator.STATUS_TS_INVALID_TILE_MGN;

        assertTrue(!this.model.hasPropertyStatus("tileMargin", code));
        verify(this.view, times(1)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("tileMargin", -1));
        assertTrue(this.model.hasPropertyStatus("tileMargin", code));
        assertEquals(Activator.getStatusMessage(code), this.model.getPropertyStatus("tileMargin", code).getMessage());
        verify(this.view, times(3)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("tileMargin", 0));
        assertTrue(!this.model.hasPropertyStatus("tileMargin", code));
        verify(this.view, times(5)).refreshProperties();
    }

    /**
     * Message 1.11 - Invalid tile spacing
     * @throws IOException
     */
    @Test
    public void testMessage111() throws IOException {
        loadEmptyFile();

        int code = Activator.STATUS_TS_INVALID_TILE_SPCN;

        assertTrue(!this.model.hasPropertyStatus("tileSpacing", code));
        verify(this.view, times(1)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("tileSpacing", -1));
        assertTrue(this.model.hasPropertyStatus("tileSpacing", code));
        assertEquals(Activator.getStatusMessage(code), this.model.getPropertyStatus("tileSpacing", code).getMessage());
        verify(this.view, times(3)).refreshProperties();

        this.model.executeOperation(propertyModel.setPropertyValue("tileSpacing", 0));
        assertTrue(!this.model.hasPropertyStatus("tileSpacing", code));
        verify(this.view, times(5)).refreshProperties();
    }

    /**
     * Refresh
     * @throws IOException
     */
    @Test
    public void testRefresh() throws Exception {
        // requirement
        testUseCase114();

        verify(this.view, times(29)).refreshProperties();
        verify(this.view, times(4)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(2)).setHulls(any(float[].class), any(int[].class), any(int[].class), any(Color[].class));
        verify(this.view, times(6)).setHullColor(anyInt(), any(Color.class));

        this.presenter.refresh();

        verify(this.view, times(30)).refreshProperties();
        verify(this.view, times(5)).setCollisionGroups(anyListOf(String.class), anyListOf(Color.class), any(String[].class));
        verify(this.view, times(3)).setHulls(any(float[].class), any(int[].class), any(int[].class), any(Color[].class));
        verify(this.view, times(6)).setHullColor(anyInt(), any(Color.class));
    }
}
