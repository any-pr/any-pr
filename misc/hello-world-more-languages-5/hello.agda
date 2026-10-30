module hello where

open import Agda.Builtin.IO
open import Agda.Builtin.String

postulate putStrLn : String -> IO {A = Set} String
{-# FOREIGN GHC import qualified Data.Text.IO as T #-}
{-# COMPILE GHC putStrLn = T.putStrLn #-}

main : IO String
main = putStrLn "Hello, World!"
