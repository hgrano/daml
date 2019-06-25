-- GENERATED by C->Haskell Compiler, version 0.28.6 Switcheroo, 25 November 2017 (Haskell)
-- Edit the ORIGNAL .chs file instead!


{-# LINE 1 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}
{-# LANGUAGE GeneralizedNewtypeDeriving #-}
{-# LANGUAGE StandaloneDeriving         #-}

module Network.GRPC.Unsafe where
import qualified Foreign.C.String as C2HSImp
import qualified Foreign.C.Types as C2HSImp
import qualified Foreign.Marshal.Utils as C2HSImp
import qualified Foreign.Ptr as C2HSImp
import qualified Foreign.Storable as C2HSImp


import Debug.Trace

import Control.Exception (bracket)
import Control.Monad

import Data.ByteString (ByteString, useAsCString)
import Data.Semigroup (Semigroup)

import Foreign.C.String (CString, peekCString)
import Foreign.Marshal.Alloc (free)
import Foreign.Ptr
import Foreign.Storable
import GHC.Exts (IsString(..))

import Network.GRPC.Unsafe.Time
{-# LINE 18 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}

import Network.GRPC.Unsafe.Constants
import Network.GRPC.Unsafe.ByteBuffer
{-# LINE 20 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}

import Network.GRPC.Unsafe.Op
{-# LINE 21 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}

import Network.GRPC.Unsafe.Metadata
{-# LINE 22 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}

import Network.GRPC.Unsafe.ChannelArgs
{-# LINE 23 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}

import Network.GRPC.Unsafe.Slice
{-# LINE 24 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}








{-# LINE 31 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


newtype StatusDetails = StatusDetails {unStatusDetails :: ByteString}
  deriving (Eq, IsString, Monoid, Semigroup, Show)

newtype CompletionQueue = CompletionQueue (C2HSImp.Ptr (CompletionQueue))
{-# LINE 36 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


deriving instance Show CompletionQueue

-- | Represents a connection to a server. Created on the client side.
newtype Channel = Channel (C2HSImp.Ptr (Channel))
{-# LINE 41 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


deriving instance Show Channel

-- | Represents a server. Created on the server side.
newtype Server = Server (C2HSImp.Ptr (Server))
{-# LINE 46 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Represents a pointer to a call. To users of the gRPC core library, this
-- type is abstract; we have no access to its fields.
newtype Call = Call (C2HSImp.Ptr (Call))
{-# LINE 50 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


deriving instance Show Call

newtype CallDetails = CallDetails (C2HSImp.Ptr (CallDetails))
{-# LINE 54 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


deriving instance Show CallDetails

createCallDetails :: IO ((CallDetails))
createCallDetails =
  createCallDetails'_ >>= \res ->
  let {res' = id res} in
  return (res')

{-# LINE 58 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}

destroyCallDetails :: (CallDetails) -> IO ()
destroyCallDetails a1 =
  let {a1' = id a1} in
  destroyCallDetails'_ a1' >>
  return ()

{-# LINE 59 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


withCallDetails :: (CallDetails -> IO a) -> IO a
withCallDetails = bracket createCallDetails destroyCallDetails

-- instance def adapted from
-- https://mail.haskell.org/pipermail/c2hs/2007-June/000800.html
instance Storable Call where
  sizeOf (Call r) = sizeOf r
  alignment (Call r) = alignment r
  peek p = fmap Call (peek (castPtr p))
  poke p (Call r) = poke (castPtr p) r

data ServerRegisterMethodPayloadHandling = SrmPayloadNone
                                         | SrmPayloadReadInitialByteBuffer
  deriving (Enum,Eq,Show)

{-# LINE 72 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | A 'Tag' is an identifier that is used with a 'CompletionQueue' to signal
-- that the corresponding operation has completed.
newtype Tag = Tag {unTag :: Ptr ()} deriving (Show, Eq)

tag :: Int -> Tag
tag = Tag . plusPtr nullPtr

noTag :: Tag
noTag = Tag nullPtr

instance Storable Tag where
  sizeOf (Tag p) = sizeOf p
  alignment (Tag p) = alignment p
  peek p = fmap Tag (peek (castPtr p))
  poke p (Tag r) = poke (castPtr p) r

-- | A 'CallHandle' is an identifier used to refer to a registered call. Create
-- one on the client with 'grpcChannelRegisterCall', and on the server with
-- 'grpcServerRegisterMethod'.
newtype CallHandle = CallHandle {unCallHandle :: Ptr ()} deriving (Show, Eq)

-- | 'Reserved' is an as-yet unused void pointer param to several gRPC
-- functions. Create one with 'reserved'.
newtype Reserved = Reserved {unReserved :: Ptr ()}

reserved :: Reserved
reserved = Reserved nullPtr

data CallError = CallOk
               | CallError
               | CallErrorNotOnServer
               | CallErrorNotOnClient
               | CallErrorAlreadyAccepted
               | CallErrorAlreadyInvoked
               | CallErrorNotInvoked
               | CallErrorAlreadyFinished
               | CallErrorTooManyOperations
               | CallErrorInvalidFlags
               | CallErrorInvalidMetadata
               | CallErrorInvalidMessage
               | CallErrorNotServerCompletionQueue
               | CallErrorBatchTooBig
               | CallErrorPayloadTypeMismatch
               | CallErrorCompletionQueueShutdown
  deriving (Show,Eq)
instance Enum CallError where
  succ CallOk = CallError
  succ CallError = CallErrorNotOnServer
  succ CallErrorNotOnServer = CallErrorNotOnClient
  succ CallErrorNotOnClient = CallErrorAlreadyAccepted
  succ CallErrorAlreadyAccepted = CallErrorAlreadyInvoked
  succ CallErrorAlreadyInvoked = CallErrorNotInvoked
  succ CallErrorNotInvoked = CallErrorAlreadyFinished
  succ CallErrorAlreadyFinished = CallErrorTooManyOperations
  succ CallErrorTooManyOperations = CallErrorInvalidFlags
  succ CallErrorInvalidFlags = CallErrorInvalidMetadata
  succ CallErrorInvalidMetadata = CallErrorInvalidMessage
  succ CallErrorInvalidMessage = CallErrorNotServerCompletionQueue
  succ CallErrorNotServerCompletionQueue = CallErrorBatchTooBig
  succ CallErrorBatchTooBig = CallErrorPayloadTypeMismatch
  succ CallErrorPayloadTypeMismatch = CallErrorCompletionQueueShutdown
  succ CallErrorCompletionQueueShutdown = error "CallError.succ: CallErrorCompletionQueueShutdown has no successor"

  pred CallError = CallOk
  pred CallErrorNotOnServer = CallError
  pred CallErrorNotOnClient = CallErrorNotOnServer
  pred CallErrorAlreadyAccepted = CallErrorNotOnClient
  pred CallErrorAlreadyInvoked = CallErrorAlreadyAccepted
  pred CallErrorNotInvoked = CallErrorAlreadyInvoked
  pred CallErrorAlreadyFinished = CallErrorNotInvoked
  pred CallErrorTooManyOperations = CallErrorAlreadyFinished
  pred CallErrorInvalidFlags = CallErrorTooManyOperations
  pred CallErrorInvalidMetadata = CallErrorInvalidFlags
  pred CallErrorInvalidMessage = CallErrorInvalidMetadata
  pred CallErrorNotServerCompletionQueue = CallErrorInvalidMessage
  pred CallErrorBatchTooBig = CallErrorNotServerCompletionQueue
  pred CallErrorPayloadTypeMismatch = CallErrorBatchTooBig
  pred CallErrorCompletionQueueShutdown = CallErrorPayloadTypeMismatch
  pred CallOk = error "CallError.pred: CallOk has no predecessor"

  enumFromTo from to = go from
    where
      end = fromEnum to
      go v = case compare (fromEnum v) end of
                 LT -> v : go (succ v)
                 EQ -> [v]
                 GT -> []

  enumFrom from = enumFromTo from CallErrorCompletionQueueShutdown

  fromEnum CallOk = 0
  fromEnum CallError = 1
  fromEnum CallErrorNotOnServer = 2
  fromEnum CallErrorNotOnClient = 3
  fromEnum CallErrorAlreadyAccepted = 4
  fromEnum CallErrorAlreadyInvoked = 5
  fromEnum CallErrorNotInvoked = 6
  fromEnum CallErrorAlreadyFinished = 7
  fromEnum CallErrorTooManyOperations = 8
  fromEnum CallErrorInvalidFlags = 9
  fromEnum CallErrorInvalidMetadata = 10
  fromEnum CallErrorInvalidMessage = 11
  fromEnum CallErrorNotServerCompletionQueue = 12
  fromEnum CallErrorBatchTooBig = 13
  fromEnum CallErrorPayloadTypeMismatch = 14
  fromEnum CallErrorCompletionQueueShutdown = 15

  toEnum 0 = CallOk
  toEnum 1 = CallError
  toEnum 2 = CallErrorNotOnServer
  toEnum 3 = CallErrorNotOnClient
  toEnum 4 = CallErrorAlreadyAccepted
  toEnum 5 = CallErrorAlreadyInvoked
  toEnum 6 = CallErrorNotInvoked
  toEnum 7 = CallErrorAlreadyFinished
  toEnum 8 = CallErrorTooManyOperations
  toEnum 9 = CallErrorInvalidFlags
  toEnum 10 = CallErrorInvalidMetadata
  toEnum 11 = CallErrorInvalidMessage
  toEnum 12 = CallErrorNotServerCompletionQueue
  toEnum 13 = CallErrorBatchTooBig
  toEnum 14 = CallErrorPayloadTypeMismatch
  toEnum 15 = CallErrorCompletionQueueShutdown
  toEnum unmatched = error ("CallError.toEnum: Cannot match " ++ show unmatched)

{-# LINE 102 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Represents the type of a completion event on a 'CompletionQueue'.
-- 'QueueShutdown' only occurs if the queue is shutting down (e.g., when the
-- server is stopping). 'QueueTimeout' occurs when we reached the deadline
-- before receiving an 'OpComplete'.
data CompletionType = QueueShutdown
                    | QueueTimeout
                    | OpComplete
  deriving (Enum,Show,Eq)

{-# LINE 109 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Represents one event received over a 'CompletionQueue'.
data Event = Event {eventCompletionType :: CompletionType,
                    eventSuccess :: Bool,
                    eventTag :: Tag}
                    deriving (Show, Eq)

instance Storable Event where
  sizeOf _ = 16
{-# LINE 118 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}

  alignment _ = 8
{-# LINE 119 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}

  peek p = Event <$> liftM (toEnum . fromIntegral) ((\ptr -> do {C2HSImp.peekByteOff ptr 0 :: IO C2HSImp.CInt}) p)
                 <*> liftM (> 0) ((\ptr -> do {C2HSImp.peekByteOff ptr 4 :: IO C2HSImp.CInt}) p)
                 <*> liftM Tag ((\ptr -> do {C2HSImp.peekByteOff ptr 8 :: IO (C2HSImp.Ptr ())}) p)
  poke p (Event c s t) = do
    (\ptr val -> do {C2HSImp.pokeByteOff ptr 0 (val :: C2HSImp.CInt)}) p $ fromIntegral $ fromEnum c
    (\ptr val -> do {C2HSImp.pokeByteOff ptr 4 (val :: C2HSImp.CInt)}) p $ if s then 1 else 0
    (\ptr val -> do {C2HSImp.pokeByteOff ptr 8 (val :: (C2HSImp.Ptr ()))}) p (unTag t)

-- | Used to unwrap structs from pointers. This is all part of a workaround
-- because the C FFI can't return raw structs directly. So we wrap the C
-- function, mallocing a temporary pointer. This function peeks the struct
-- within the pointer, then frees it.
castPeek :: Storable b => Ptr a -> IO b
castPeek p = do
  val <- peek (castPtr p)
  free p
  return val

data ConnectivityState = ChannelIdle
                       | ChannelConnecting
                       | ChannelReady
                       | ChannelTransientFailure
                       | ChannelShutdown
  deriving (Enum,Show,Eq)

{-# LINE 139 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcInit :: IO ()
grpcInit =
  grpcInit'_ >>
  return ()

{-# LINE 141 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcShutdown :: IO ()
grpcShutdown =
  grpcShutdown'_ >>
  return ()

{-# LINE 143 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcVersionString :: IO ((String))
grpcVersionString =
  grpcVersionString'_ >>= \res ->
  C2HSImp.peekCString res >>= \res' ->
  return (res')

{-# LINE 145 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Create a new 'CompletionQueue' for GRPC_CQ_NEXT. See the docs for
-- 'grpcCompletionQueueShutdown' for instructions on how to clean up afterwards.
grpcCompletionQueueCreateForNext :: (Reserved) -> IO ((CompletionQueue))
grpcCompletionQueueCreateForNext a1 =
  let {a1' = unReserved a1} in 
  grpcCompletionQueueCreateForNext'_ a1' >>= \res ->
  let {res' = id res} in
  return (res')

{-# LINE 150 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Create a new 'CompletionQueue' for GRPC_CQ_PLUCK. See the docs for
-- 'grpcCompletionQueueShutdown' for instructions on how to clean up afterwards.
grpcCompletionQueueCreateForPluck :: (Reserved) -> IO ((CompletionQueue))
grpcCompletionQueueCreateForPluck a1 =
  let {a1' = unReserved a1} in 
  grpcCompletionQueueCreateForPluck'_ a1' >>= \res ->
  let {res' = id res} in
  return (res')

{-# LINE 155 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Block until we get the next event off the given 'CompletionQueue',
-- using the given 'CTimeSpecPtr' as a deadline specifying the max amount of
-- time to block.
grpcCompletionQueueNext :: (CompletionQueue) -> (CTimeSpecPtr) -> (Reserved) -> IO ((Event))
grpcCompletionQueueNext a1 a2 a3 =
  let {a1' = id a1} in 
  let {a2' = id a2} in 
  let {a3' = unReserved a3} in 
  grpcCompletionQueueNext'_ a1' a2' a3' >>= \res ->
  castPeek res >>= \res' ->
  return (res')

{-# LINE 162 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Block until we get the next event with the given 'Tag' off the given
-- 'CompletionQueue'. NOTE: No more than 'maxCompletionQueuePluckers' can call
-- this function concurrently!
grpcCompletionQueuePluck :: (CompletionQueue) -> (Tag) -> (CTimeSpecPtr) -> (Reserved) -> IO ((Event))
grpcCompletionQueuePluck a1 a2 a3 a4 =
  let {a1' = id a1} in 
  let {a2' = unTag a2} in 
  let {a3' = id a3} in 
  let {a4' = unReserved a4} in 
  grpcCompletionQueuePluck'_ a1' a2' a3' a4' >>= \res ->
  castPeek res >>= \res' ->
  return (res')

{-# LINE 169 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Stops a completion queue. After all events are drained,
-- 'grpcCompletionQueueNext' will yield 'QueueShutdown' and then it is safe to
-- call 'grpcCompletionQueueDestroy'. After calling this, we must ensure no
-- new work is pushed to the queue.
grpcCompletionQueueShutdown :: (CompletionQueue) -> IO ()
grpcCompletionQueueShutdown a1 =
  let {a1' = id a1} in 
  grpcCompletionQueueShutdown'_ a1' >>
  return ()

{-# LINE 175 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Destroys a 'CompletionQueue'. See 'grpcCompletionQueueShutdown' for how to
-- use safely. Caller must ensure no threads are calling
-- 'grpcCompletionQueueNext'.
grpcCompletionQueueDestroy :: (CompletionQueue) -> IO ()
grpcCompletionQueueDestroy a1 =
  let {a1' = id a1} in 
  grpcCompletionQueueDestroy'_ a1' >>
  return ()

{-# LINE 180 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Sets up a call on the client. The first string is the endpoint name (e.g.
-- @"/foo"@) and the second is the host name. In my tests so far, the host name
-- here doesn't seem to be used... it looks like the host and port specified in
-- 'grpcInsecureChannelCreate' is the one that is actually used.
grpcChannelCreateCall :: (Channel) -> (Call) -> (PropagationMask) -> (CompletionQueue) -> (ByteString) -> (ByteString) -> (CTimeSpecPtr) -> (Reserved) -> IO ((Call))
grpcChannelCreateCall a1 a2 a3 a4 a5 a6 a7 a8 =
  let {a1' = id a1} in 
  let {a2' = id a2} in 
  let {a3' = fromIntegral a3} in 
  let {a4' = id a4} in 
  useAsCString a5 $ \a5' -> 
  useAsCString a6 $ \a6' -> 
  let {a7' = id a7} in 
  let {a8' = unReserved a8} in 
  grpcChannelCreateCall'_ a1' a2' a3' a4' a5' a6' a7' a8' >>= \res ->
  let {res' = id res} in
  return (res')

{-# LINE 189 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Create a channel (on the client) to the server. The first argument is
-- host and port, e.g. @"localhost:50051"@. The gRPC docs say that most clients
-- are expected to pass a 'nullPtr' for the 'ChannelArgsPtr'. We currently don't
-- expose any functions for creating channel args, since they are entirely
-- undocumented.
grpcInsecureChannelCreate :: (ByteString) -> (GrpcChannelArgs) -> (Reserved) -> IO ((Channel))
grpcInsecureChannelCreate a1 a2 a3 =
  useAsCString a1 $ \a1' ->
  let {a2' = trace ("GrpcChannelArgs: " ++ show a2) $ id a2} in
  let {a3' = trace ("before grpcInsecureChannelCreate'_ call") $ unReserved a3} in
  grpcInsecureChannelCreate'_ a1' a2' a3' >>= \res ->
  let {res' = trace ("after grpcInsecureChannelCreate'_ call") $ id res} in
  return (res')

{-# LINE 197 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcChannelRegisterCall :: (Channel) -> (ByteString) -> (ByteString) -> (Reserved) -> IO ((CallHandle))
grpcChannelRegisterCall a1 a2 a3 a4 =
  let {a1' = id a1} in 
  useAsCString a2 $ \a2' -> 
  useAsCString a3 $ \a3' -> 
  let {a4' = unReserved a4} in 
  grpcChannelRegisterCall'_ a1' a2' a3' a4' >>= \res ->
  let {res' = CallHandle res} in
  return (res')

{-# LINE 201 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcChannelCreateRegisteredCall :: (Channel) -> (Call) -> (PropagationMask) -> (CompletionQueue) -> (CallHandle) -> (CTimeSpecPtr) -> (Reserved) -> IO ((Call))
grpcChannelCreateRegisteredCall a1 a2 a3 a4 a5 a6 a7 =
  let {a1' = id a1} in 
  let {a2' = id a2} in 
  let {a3' = fromIntegral a3} in 
  let {a4' = id a4} in 
  let {a5' = unCallHandle a5} in 
  let {a6' = id a6} in 
  let {a7' = unReserved a7} in 
  grpcChannelCreateRegisteredCall'_ a1' a2' a3' a4' a5' a6' a7' >>= \res ->
  let {res' = id res} in
  return (res')

{-# LINE 205 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | get the current connectivity state of the given channel. The 'Bool' is
-- True if we should try to connect the channel.
grpcChannelCheckConnectivityState :: (Channel) -> (Bool) -> IO ((ConnectivityState))
grpcChannelCheckConnectivityState a1 a2 =
  let {a1' = id a1} in 
  let {a2' = C2HSImp.fromBool a2} in 
  grpcChannelCheckConnectivityState'_ a1' a2' >>= \res ->
  let {res' = (toEnum . fromIntegral) res} in
  return (res')

{-# LINE 210 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | When the current connectivity state changes from the given
-- 'ConnectivityState', enqueues a success=1 tag on the given 'CompletionQueue'.
-- If the deadline is reached, enqueues a tag with success=0.
grpcChannelWatchConnectivityState :: (Channel) -> (ConnectivityState) -> (CTimeSpecPtr) -> (CompletionQueue) -> (Tag) -> IO ()
grpcChannelWatchConnectivityState a1 a2 a3 a4 a5 =
  let {a1' = id a1} in 
  let {a2' = (fromIntegral . fromEnum) a2} in 
  let {a3' = id a3} in 
  let {a4' = id a4} in 
  let {a5' = unTag a5} in 
  grpcChannelWatchConnectivityState'_ a1' a2' a3' a4' a5' >>
  return ()

{-# LINE 218 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcChannelPing :: (Channel) -> (CompletionQueue) -> (Tag) -> (Reserved) -> IO ()
grpcChannelPing a1 a2 a3 a4 =
  let {a1' = id a1} in 
  let {a2' = id a2} in 
  let {a3' = unTag a3} in 
  let {a4' = unReserved a4} in 
  grpcChannelPing'_ a1' a2' a3' a4' >>
  return ()

{-# LINE 221 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcChannelDestroy :: (Channel) -> IO ()
grpcChannelDestroy a1 =
  let {a1' = id a1} in 
  grpcChannelDestroy'_ a1' >>
  return ()

{-# LINE 223 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Starts executing a batch of ops in the given 'OpArray'. Does not block.
-- When complete, an event identified by the given 'Tag'
-- will be pushed onto the 'CompletionQueue' that was associated with the given
-- 'Call' when the 'Call' was created.
grpcCallStartBatch :: (Call) -> (OpArray) -> (Int) -> (Tag) -> (Reserved) -> IO ((CallError))
grpcCallStartBatch a1 a2 a3 a4 a5 =
  let {a1' = id a1} in 
  let {a2' = id a2} in 
  let {a3' = fromIntegral a3} in 
  let {a4' = unTag a4} in 
  let {a5' = unReserved a5} in 
  grpcCallStartBatch'_ a1' a2' a3' a4' a5' >>= \res ->
  let {res' = (toEnum . fromIntegral) res} in
  return (res')

{-# LINE 230 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcCallCancel :: (Call) -> (Reserved) -> IO ()
grpcCallCancel a1 a2 =
  let {a1' = id a1} in 
  let {a2' = unReserved a2} in 
  grpcCallCancel'_ a1' a2' >>
  return ()

{-# LINE 232 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcCallCancelWithStatus :: (Call) -> (StatusCode) -> (String) -> (Reserved) -> IO ()
grpcCallCancelWithStatus a1 a2 a3 a4 =
  let {a1' = id a1} in 
  let {a2' = (fromIntegral . fromEnum) a2} in 
  C2HSImp.withCString a3 $ \a3' -> 
  let {a4' = unReserved a4} in 
  grpcCallCancelWithStatus'_ a1' a2' a3' a4' >>
  return ()

{-# LINE 235 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcCallRef :: (Call) -> IO ()
grpcCallRef a1 =
  let {a1' = id a1} in 
  grpcCallRef'_ a1' >>
  return ()

{-# LINE 237 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcCallUnref :: (Call) -> IO ()
grpcCallUnref a1 =
  let {a1' = id a1} in 
  grpcCallUnref'_ a1' >>
  return ()

{-# LINE 239 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Gets the peer of the current call as a string.
grpcCallGetPeer :: (Call) -> IO ((String))
grpcCallGetPeer a1 =
  let {a1' = id a1} in 
  grpcCallGetPeer'_ a1' >>= \res ->
  getPeerPeek res >>= \res' ->
  return (res')

{-# LINE 242 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


gprFree :: (Ptr ()) -> IO ()
gprFree a1 =
  let {a1' = id a1} in 
  gprFree'_ a1' >>
  return ()

{-# LINE 244 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


getPeerPeek :: CString -> IO String
getPeerPeek cstr = do
  haskellStr <- peekCString cstr
  gprFree (castPtr cstr)
  return haskellStr

-- Server stuff

grpcServerCreate :: (GrpcChannelArgs) -> (Reserved) -> IO ((Server))
grpcServerCreate a1 a2 =
  let {a1' = id a1} in 
  let {a2' = unReserved a2} in 
  grpcServerCreate'_ a1' a2' >>= \res ->
  let {res' = id res} in
  return (res')

{-# LINE 255 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcServerRegisterMethod :: (Server) -> (ByteString) -> (ByteString) -> (ServerRegisterMethodPayloadHandling) -> IO ((CallHandle))
grpcServerRegisterMethod a1 a2 a3 a4 =
  let {a1' = id a1} in 
  useAsCString a2 $ \a2' -> 
  useAsCString a3 $ \a3' -> 
  let {a4' = (fromIntegral . fromEnum) a4} in 
  grpcServerRegisterMethod'_ a1' a2' a3' a4' >>= \res ->
  let {res' = CallHandle res} in
  return (res')

{-# LINE 258 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcServerRegisterCompletionQueue :: (Server) -> (CompletionQueue) -> (Reserved) -> IO ()
grpcServerRegisterCompletionQueue a1 a2 a3 =
  let {a1' = id a1} in 
  let {a2' = id a2} in 
  let {a3' = unReserved a3} in 
  grpcServerRegisterCompletionQueue'_ a1' a2' a3' >>
  return ()

{-# LINE 261 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcServerAddInsecureHttp2Port :: (Server) -> (ByteString) -> IO ((Int))
grpcServerAddInsecureHttp2Port a1 a2 =
  let {a1' = id a1} in 
  useAsCString a2 $ \a2' -> 
  grpcServerAddInsecureHttp2Port'_ a1' a2' >>= \res ->
  let {res' = fromIntegral res} in
  return (res')

{-# LINE 264 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Starts a server. To shut down the server, call these in order:
-- 'grpcServerShutdownAndNotify', 'grpcServerCancelAllCalls',
-- 'grpcServerDestroy'. After these are done, shut down and destroy the server's
-- completion queue with 'grpcCompletionQueueShutdown' followed by
-- 'grpcCompletionQueueDestroy'.
grpcServerStart :: (Server) -> IO ()
grpcServerStart a1 =
  let {a1' = id a1} in 
  grpcServerStart'_ a1' >>
  return ()

{-# LINE 271 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcServerShutdownAndNotify :: (Server) -> (CompletionQueue) -> (Tag) -> IO ()
grpcServerShutdownAndNotify a1 a2 a3 =
  let {a1' = id a1} in 
  let {a2' = id a2} in 
  let {a3' = unTag a3} in 
  grpcServerShutdownAndNotify'_ a1' a2' a3' >>
  return ()

{-# LINE 274 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


grpcServerCancelAllCalls :: (Server) -> IO ()
grpcServerCancelAllCalls a1 =
  let {a1' = id a1} in 
  grpcServerCancelAllCalls'_ a1' >>
  return ()

{-# LINE 277 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Destroy the server. See 'grpcServerStart' for complete shutdown
-- instructions.
grpcServerDestroy :: (Server) -> IO ()
grpcServerDestroy a1 =
  let {a1' = id a1} in 
  grpcServerDestroy'_ a1' >>
  return ()

{-# LINE 281 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Request a call.
-- NOTE: You need to call 'grpcCompletionQueueNext' or
-- 'grpcCompletionQueuePluck' on the completion queue with the given
-- 'Tag' before using the 'Call' pointer again.
grpcServerRequestCall :: (Server) -> (Ptr Call) -> (CallDetails) -> (MetadataArray) -> (CompletionQueue) -> (CompletionQueue) -> (Tag) -> IO ((CallError))
grpcServerRequestCall a1 a2 a3 a4 a5 a6 a7 =
  let {a1' = id a1} in 
  let {a2' = id a2} in 
  let {a3' = id a3} in 
  let {a4' = id a4} in 
  let {a5' = id a5} in 
  let {a6' = id a6} in 
  let {a7' = unTag a7} in 
  grpcServerRequestCall'_ a1' a2' a3' a4' a5' a6' a7' >>= \res ->
  let {res' = (toEnum . fromIntegral) res} in
  return (res')

{-# LINE 290 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


-- | Request a registered call for the given registered method described by a
-- 'CallHandle'. The call, deadline, metadata array, and byte buffer are all
-- out parameters.
grpcServerRequestRegisteredCall :: (Server) -> (CallHandle) -> (Ptr Call) -> (CTimeSpecPtr) -> (MetadataArray) -> (Ptr ByteBuffer) -> (CompletionQueue) -> (CompletionQueue) -> (Tag) -> IO ((CallError))
grpcServerRequestRegisteredCall a1 a2 a3 a4 a5 a6 a7 a8 a9 =
  let {a1' = id a1} in 
  let {a2' = unCallHandle a2} in 
  let {a3' = id a3} in 
  let {a4' = id a4} in 
  let {a5' = id a5} in 
  let {a6' = id a6} in 
  let {a7' = id a7} in 
  let {a8' = id a8} in 
  let {a9' = unTag a9} in 
  grpcServerRequestRegisteredCall'_ a1' a2' a3' a4' a5' a6' a7' a8' a9' >>= \res ->
  let {res' = (toEnum . fromIntegral) res} in
  return (res')

{-# LINE 299 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


callDetailsGetMethod :: (CallDetails) -> IO ((ByteString))
callDetailsGetMethod a1 =
  let {a1' = id a1} in 
  callDetailsGetMethod'_ a1' >>= \res ->
  sliceToByteString res >>= \res' ->
  return (res')

{-# LINE 301 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


callDetailsGetHost :: (CallDetails) -> IO ((ByteString))
callDetailsGetHost a1 =
  let {a1' = id a1} in 
  callDetailsGetHost'_ a1' >>= \res ->
  sliceToByteString res >>= \res' ->
  return (res')

{-# LINE 303 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


callDetailsGetDeadline :: (CallDetails) -> IO ((CTimeSpec))
callDetailsGetDeadline a1 =
  let {a1' = id a1} in 
  callDetailsGetDeadline'_ a1' >>= \res ->
  peek res >>= \res' ->
  return (res')

{-# LINE 305 "nix/third-party/gRPC-haskell/core/src/Network/GRPC/Unsafe.chs" #-}


foreign import ccall unsafe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h create_call_details"
  createCallDetails'_ :: (IO (CallDetails))

foreign import ccall unsafe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h destroy_call_details"
  destroyCallDetails'_ :: ((CallDetails) -> (IO ()))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_init"
  grpcInit'_ :: (IO ())

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_shutdown"
  grpcShutdown'_ :: (IO ())

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_version_string"
  grpcVersionString'_ :: (IO (C2HSImp.Ptr C2HSImp.CChar))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_completion_queue_create_for_next"
  grpcCompletionQueueCreateForNext'_ :: ((C2HSImp.Ptr ()) -> (IO (CompletionQueue)))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_completion_queue_create_for_pluck"
  grpcCompletionQueueCreateForPluck'_ :: ((C2HSImp.Ptr ()) -> (IO (CompletionQueue)))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_completion_queue_next_"
  grpcCompletionQueueNext'_ :: ((CompletionQueue) -> ((CTimeSpecPtr) -> ((C2HSImp.Ptr ()) -> (IO (C2HSImp.Ptr ())))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_completion_queue_pluck_"
  grpcCompletionQueuePluck'_ :: ((CompletionQueue) -> ((C2HSImp.Ptr ()) -> ((CTimeSpecPtr) -> ((C2HSImp.Ptr ()) -> (IO (C2HSImp.Ptr ()))))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_completion_queue_shutdown"
  grpcCompletionQueueShutdown'_ :: ((CompletionQueue) -> (IO ()))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_completion_queue_destroy"
  grpcCompletionQueueDestroy'_ :: ((CompletionQueue) -> (IO ()))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_channel_create_call_"
  grpcChannelCreateCall'_ :: ((Channel) -> ((Call) -> (C2HSImp.CUInt -> ((CompletionQueue) -> ((C2HSImp.Ptr C2HSImp.CChar) -> ((C2HSImp.Ptr C2HSImp.CChar) -> ((CTimeSpecPtr) -> ((C2HSImp.Ptr ()) -> (IO (Call))))))))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_insecure_channel_create"
  grpcInsecureChannelCreate'_ :: ((C2HSImp.Ptr C2HSImp.CChar) -> ((GrpcChannelArgs) -> ((C2HSImp.Ptr ()) -> (IO (Channel)))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_channel_register_call"
  grpcChannelRegisterCall'_ :: ((Channel) -> ((C2HSImp.Ptr C2HSImp.CChar) -> ((C2HSImp.Ptr C2HSImp.CChar) -> ((C2HSImp.Ptr ()) -> (IO (C2HSImp.Ptr ()))))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_channel_create_registered_call_"
  grpcChannelCreateRegisteredCall'_ :: ((Channel) -> ((Call) -> (C2HSImp.CUInt -> ((CompletionQueue) -> ((C2HSImp.Ptr ()) -> ((CTimeSpecPtr) -> ((C2HSImp.Ptr ()) -> (IO (Call)))))))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_channel_check_connectivity_state"
  grpcChannelCheckConnectivityState'_ :: ((Channel) -> (C2HSImp.CInt -> (IO C2HSImp.CInt)))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_channel_watch_connectivity_state_"
  grpcChannelWatchConnectivityState'_ :: ((Channel) -> (C2HSImp.CInt -> ((CTimeSpecPtr) -> ((CompletionQueue) -> ((C2HSImp.Ptr ()) -> (IO ()))))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_channel_ping"
  grpcChannelPing'_ :: ((Channel) -> ((CompletionQueue) -> ((C2HSImp.Ptr ()) -> ((C2HSImp.Ptr ()) -> (IO ())))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_channel_destroy"
  grpcChannelDestroy'_ :: ((Channel) -> (IO ()))

foreign import ccall unsafe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_call_start_batch"
  grpcCallStartBatch'_ :: ((Call) -> ((OpArray) -> (C2HSImp.CULLong -> ((C2HSImp.Ptr ()) -> ((C2HSImp.Ptr ()) -> (IO C2HSImp.CInt))))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_call_cancel"
  grpcCallCancel'_ :: ((Call) -> ((C2HSImp.Ptr ()) -> (IO C2HSImp.CInt)))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_call_cancel_with_status"
  grpcCallCancelWithStatus'_ :: ((Call) -> (C2HSImp.CInt -> ((C2HSImp.Ptr C2HSImp.CChar) -> ((C2HSImp.Ptr ()) -> (IO C2HSImp.CInt)))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_call_ref"
  grpcCallRef'_ :: ((Call) -> (IO ()))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_call_unref"
  grpcCallUnref'_ :: ((Call) -> (IO ()))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_call_get_peer"
  grpcCallGetPeer'_ :: ((Call) -> (IO (C2HSImp.Ptr C2HSImp.CChar)))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h gpr_free"
  gprFree'_ :: ((C2HSImp.Ptr ()) -> (IO ()))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_server_create"
  grpcServerCreate'_ :: ((GrpcChannelArgs) -> ((C2HSImp.Ptr ()) -> (IO (Server))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_server_register_method_"
  grpcServerRegisterMethod'_ :: ((Server) -> ((C2HSImp.Ptr C2HSImp.CChar) -> ((C2HSImp.Ptr C2HSImp.CChar) -> (C2HSImp.CInt -> (IO (C2HSImp.Ptr ()))))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_server_register_completion_queue"
  grpcServerRegisterCompletionQueue'_ :: ((Server) -> ((CompletionQueue) -> ((C2HSImp.Ptr ()) -> (IO ()))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_server_add_insecure_http2_port"
  grpcServerAddInsecureHttp2Port'_ :: ((Server) -> ((C2HSImp.Ptr C2HSImp.CChar) -> (IO C2HSImp.CInt)))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_server_start"
  grpcServerStart'_ :: ((Server) -> (IO ()))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_server_shutdown_and_notify"
  grpcServerShutdownAndNotify'_ :: ((Server) -> ((CompletionQueue) -> ((C2HSImp.Ptr ()) -> (IO ()))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_server_cancel_all_calls"
  grpcServerCancelAllCalls'_ :: ((Server) -> (IO ()))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_server_destroy"
  grpcServerDestroy'_ :: ((Server) -> (IO ()))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_server_request_call"
  grpcServerRequestCall'_ :: ((Server) -> ((C2HSImp.Ptr (Call)) -> ((CallDetails) -> ((MetadataArray) -> ((CompletionQueue) -> ((CompletionQueue) -> ((C2HSImp.Ptr ()) -> (IO C2HSImp.CInt))))))))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h grpc_server_request_registered_call"
  grpcServerRequestRegisteredCall'_ :: ((Server) -> ((C2HSImp.Ptr ()) -> ((C2HSImp.Ptr (Call)) -> ((CTimeSpecPtr) -> ((MetadataArray) -> ((C2HSImp.Ptr (ByteBuffer)) -> ((CompletionQueue) -> ((CompletionQueue) -> ((C2HSImp.Ptr ()) -> (IO C2HSImp.CInt))))))))))

foreign import ccall unsafe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h call_details_get_method"
  callDetailsGetMethod'_ :: ((CallDetails) -> (IO (Slice)))

foreign import ccall unsafe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h call_details_get_host"
  callDetailsGetHost'_ :: ((CallDetails) -> (IO (Slice)))

foreign import ccall safe "bazel-out/k8-fastbuild/bin/nix/third-party/gRPC-haskell/core/chs-src_Network_GRPC_Unsafe.chs/Network/GRPC/Unsafe.chs.h call_details_get_deadline"
  callDetailsGetDeadline'_ :: ((CallDetails) -> (IO (CTimeSpecPtr)))
