/*
 * Copyright 2018 PayPal Inc.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.paypal.gimel.sql

import scala.collection.mutable.ListBuffer
import scala.util._

import org.apache.hadoop.hive.ql.parse.{ASTNode, HiveParser, ParseDriver}

import com.paypal.gimel.logger.Logger

object SQLParser extends Logger {

  /**
    * getSourceTables - Helper function to call a function which is recursive to get the source table names from the AST
    *
    * @param sql to be parsed
    * @return - List of source table names
    */

  def getSourceTables(sql: String): ListBuffer[String] = {
    val parsDri = new ParseDriver()
    val ast_tree: ASTNode = parsDri.parse(sql)
    getSourceTables(ast_tree)
  }

  /**
    * getTargetTables - Helper function to call a function which is recursive to get the Target table names from the AST
    *
    * @param sql to be parsed
    * @return - List of target tables if any. If it is select only table, it returns a None.
    */

  def getTargetTables1(sql: String): Option[String] = {
    val parsDri = new ParseDriver()
    val ast_tree: ASTNode = parsDri.parse(sql)
    val targetTableName = getTargetTables(ast_tree)
    if (targetTableName.isEmpty) None else Some(targetTableName.head)
  }


  /**
    * getTargetTables1 - Helper function to call a function which is recursive to get the Target table names from the AST
    *
    * @param sql to be parsed
    * @return - List of target tables if any. If it is select only table, it returns a None.
    */

  def getTargetTables(sql: String): Option[String] = {
    Try {
      GimelQueryUtils.isHavingInsert(sql) match {
        case false => None
        case true =>
          val lSql = sql.toLowerCase()
          val tokens = GimelQueryUtils.tokenizeSql(lSql)
          val tableIndex = tokens.contains("table") match {
            case true => tokens.indexOf("table")
            case false => tokens.indexOf("into")
          }
          Some(tokens(tableIndex + 1))
      }
    } match {
      case Success(x) => x
      case Failure(f) =>
        throw new Exception(
          s"""
             |ERROR PARSING SQL IN Gimel --> ${sql}
             |Exception --> ${f}
             |PLEASE VALIDATE IF SQL IS FORMED CORRECTLY.
         """.stripMargin)
    }
  }

  // TODO - Following two functions can be combined later.

  /**
    * getSourceTables - Recursive function to get the source table names
    *
    * @param from   - AST tree
    * @param myList - list of source table names
    */
  private def getSourceTables(from: ASTNode, myList: ListBuffer[String] = new ListBuffer[String]()): ListBuffer[String] = {
    var table: String = ""

    if (from != null) {

      if (HiveParser.TOK_TABREF == from.getType) {
        val tabName = from.getChild(0)

        if (HiveParser.TOK_TABNAME == tabName.getType) {
          if (tabName.getChildCount == 2) {
            table = tabName.getChild(0).getText + "." + tabName.getChild(1).getText
          } else {
            table = tabName.getChild(0).getText
          }
          myList += table
        }
      }

      for (i <- 0 to from.getChildCount) {
        val child = from.getChild(i)
        if (child != null) {
          getSourceTables(child.asInstanceOf[ASTNode], myList)
        }
      }
    }
    myList
  }

  /**
    * getTargetTables - Recursive function to get the target tables
    *
    * @param from   - AST tree
    * @param myList - List of target tables if any.
    */
  private def getTargetTables(from: ASTNode, myList: ListBuffer[String] = new ListBuffer[String]()): ListBuffer[String] = {
    var table: String = ""

    if (from != null) {
      if (HiveParser.TOK_INSERT_INTO == from.getType) {
        val tok_tab = from.getChild(0)

        if (HiveParser.TOK_TAB == tok_tab.getType) {
          if (tok_tab.getChild(0).getChildCount == 2) {
            val tabName = tok_tab.getChild(0)
            table = tabName.getChild(0).getText + "." + tabName.getChild(1).getText
            myList += table
          }

        }
      }
      for (i <- 0 to from.getChildCount) {
        val child = from.getChild(i)
        if (child != null) {
          getTargetTables(child.asInstanceOf[ASTNode], myList)
        }
      }
    }
    myList
  }

}
