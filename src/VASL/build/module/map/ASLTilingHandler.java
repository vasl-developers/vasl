package VASL.build.module.map;

//import static VASSAL.tools.image.tilecache.ZipFileImageTilerState.STARTING_IMAGE;
//import static VASSAL.tools.image.tilecache.ZipFileImageTilerState.TILE_WRITTEN;
//import static VASSAL.tools.image.tilecache.ZipFileImageTilerState.TILING_FINISHED;

import java.awt.Dimension;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Future;

import VASL.build.module.ASLMap;
import VASL.build.module.map.boardArchive.BoardArchive;
import VASL.build.module.map.boardPicker.VASLBoard;

import VASSAL.launch.TilingHandler;
import VASSAL.tools.DataArchive;
import VASSAL.tools.io.FileArchive;

public class ASLTilingHandler extends VASSAL.launch.TilingHandler {
  public ASLTilingHandler(String aname, File cdir, Dimension tdim, int mhlim) {
    super(aname, cdir, tdim, mhlim);
  }


  protected Iterable<String> getImagePaths(DataArchive archive) throws IOException {
    final FileArchive fa = archive.getArchive();
    // png code - June 2019 allows board image files to be in either png or gif format
    final BoardArchive VASLBoardArchive = new BoardArchive(
      fa.getName(), "", ASLMap.getSharedBoardMetadata()
    );

    return List.of(
      VASLBoardArchive.isLegacyBoard() ?
        fa.getFile().getName() + ".gif" :
        VASLBoardArchive.getBoardImageFileName()
    );
  }

  @Override
  protected StateMachineHandler createStateMachineHandler(int tcount, Future<Integer> fut) {
    return new StateMachineHandler() {
      @Override
      public void handleStart() {
      }

      @Override
      public void handleStartingImageState(String ipath) {
      }

      @Override
      public void handleTileWrittenState() {
      }

      @Override
      public void handleTilingFinishedState() {
      }

      @Override
      public void handleSuccess() {
      }

      @Override
      public void handleFailure() {
      }
    };
  }
}
