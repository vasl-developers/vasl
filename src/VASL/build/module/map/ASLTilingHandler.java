package VASL.build.module.map;

import static VASSAL.tools.image.tilecache.ZipFileImageTilerState.STARTING_IMAGE;
import static VASSAL.tools.image.tilecache.ZipFileImageTilerState.TILE_WRITTEN;
import static VASSAL.tools.image.tilecache.ZipFileImageTilerState.TILING_FINISHED;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.DataInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import VASL.build.module.ASLMap;
import VASL.build.module.map.boardArchive.BoardArchive;
import VASSAL.Info;
import VASSAL.launch.TilingHandler;
import VASSAL.build.GameModule;
import VASSAL.tools.DataArchive;
import VASSAL.tools.image.ImageUtils;
import VASSAL.tools.image.tilecache.TileUtils;
import VASSAL.tools.io.FileArchive;
import VASSAL.tools.io.FileStore;
import VASSAL.tools.io.IOUtils;
import VASSAL.tools.io.InputOutputStreamPump;
import VASSAL.tools.io.InputStreamPump;
import VASSAL.tools.io.ProcessLauncher;
import VASSAL.tools.io.ProcessWrapper;
import VASSAL.tools.lang.Pair;
import VASSAL.tools.swing.EDT;
import VASSAL.tools.swing.ProgressDialog;
import VASSAL.tools.swing.Progressor;

public class ASLTilingHandler extends VASSAL.launch.TilingHandler {
  public ASLTilingHandler(
    String aname,
    File cdir,
    Dimension tdim,
    int mhlim,
    int pid)
  {
    super(aname, cdir, tdim, mhlim, pid);
  }

  @Override
  protected Dimension getImageSize(DataArchive archive, String iname)
                                                           throws IOException {
    InputStream in = null;
    try {
      in = archive.getInputStream(iname);
      final Dimension id = ImageUtils.getImageSize(iname, in);
      in.close();
      return id;
    }
    finally {
      IOUtils.closeQuietly(in);
    }
  }

  @Override
  protected Pair<Integer,Integer> findImages(
    DataArchive archive,
    FileStore tcache,
    List<String> multi,
    List<Pair<String,IOException>> failed) throws IOException
  {
    final FileArchive fa = archive.getArchive();

    // png code - May 2019 allows board image files to be in either of png and gif formats
    BoardArchive VASLBoardArchive = new BoardArchive(fa.getName(), "", ASLMap.getSharedBoardMetadata());
    final String iname =  VASLBoardArchive.getBoardImageFileName();  //fa.getFile().getName() + ".gif";
    //
    int maxpix = 0; // number of pixels in the largest image
    int tcount = 0; // tile count

    // look at the first 1:1 tile
    final String tpath = TileUtils.tileName(iname, 0, 0, 1);

    // check whether the image is older than the tile
    final long imtime = fa.getMTime(iname);

    // skip images with fresh tiles
    if (imtime <= 0 || // time in archive might be goofy
        imtime > tcache.getMTime(tpath)) {
      final Dimension idim;
      try {
        idim = getImageSize(archive, iname);
      }
      catch (IOException e) {
        // skip images we can't read
        failed.add(Pair.of(iname, e));
        return new Pair<Integer,Integer>(0, 0);
      }

      // count the tiles at all sizes if we have more than one tile at 1:1
      final int t = TileUtils.tileCountAtScale(idim, tdim, 1) > 1 ?
                    TileUtils.tileCount(idim, tdim) : 0;

      if (t > 0) {
        tcount = t;
        multi.add(iname);
        maxpix = idim.width * idim.height;
      }
    }

    return new Pair<Integer,Integer>(tcount, maxpix);
  }

  @Override
  protected void runSlicer(List<String> multi, final int tcount, int maxheap)
                                   throws CancellationException, IOException {

    final InetAddress lo = InetAddress.getByName(null);
    final ServerSocket ssock = new ServerSocket(0, 0, lo);

    final int port = ssock.getLocalPort();

    final List<String> args = new ArrayList<String>();
    args.addAll(Arrays.asList(new String[] {
      Info.javaBinPath,
      "-classpath",
      System.getProperty("java.class.path"),
      "-Xmx" + maxheap + "M",
      "-DVASSAL.id=" + pid,
      "-Duser.home=" + System.getProperty("user.home"),
      "-DVASSAL.port=" + port,
      "VASSAL.tools.image.tilecache.ZipFileImageTiler",
      aname,
      cdir.getAbsolutePath(),
      String.valueOf(tdim.width),
      String.valueOf(tdim.height)
    }));

    args.addAll(multi);

    // set up the process
    final InputStreamPump outP = new InputOutputStreamPump(null, System.out);
    final InputStreamPump errP = new InputOutputStreamPump(null, System.err);

    final ProcessWrapper proc = new ProcessLauncher().launch(
      null,
      outP,
      errP,
      args.toArray(new String[args.size()])
    );

    // write the image paths to child's stdin, one per line
    PrintWriter stdin = null;
    try {
      stdin = new PrintWriter(proc.stdin);
      for (String m : multi) {
        stdin.println(m);
      }
    }
    finally {
      IOUtils.closeQuietly(stdin);
    }

    Socket csock = null;
    DataInputStream in = null;
    try {
      csock = ssock.accept();
      csock.shutdownOutput();

      in = new DataInputStream(csock.getInputStream());

      boolean done = false;
      byte type;
      while (!done) {
        type = in.readByte();

        switch (type) {
        case STARTING_IMAGE:
          in.readUTF();
          break;

        case TILE_WRITTEN:
          break;

        case TILING_FINISHED:
          done = true;
          break;

        default:
          throw new IllegalStateException("bad type: " + type);
        }
      }

      in.close();
      csock.close();
      ssock.close();
    }
    catch (IOException e) {

    }
    finally {
      IOUtils.closeQuietly(in);
      IOUtils.closeQuietly(csock);
      IOUtils.closeQuietly(ssock);
    }

    // wait for the tiling process to end
    try {
      final int retval = proc.future.get();
      if (retval != 0) {
        throw new IOException("return value == " + retval);
      }
    }
    catch (ExecutionException e) {
      // should never happen
      throw new IllegalStateException(e);
    }
    catch (InterruptedException e) {
      // should never happen
      throw new IllegalStateException(e);
    }
  }
}
