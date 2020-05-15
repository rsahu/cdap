/*
 * Copyright © 2020 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import React from 'react';
import VirtualScroll from './index';
import withStyles from '@material-ui/core/styles/withStyles';

const styles = () => {
  return {
    root: {
      height: 30,
      lineHeight: '30px',
      display: 'flex',
      justifyContent: 'space-between',
      padding: '0 10px',
    },
    row: {
      lineHeight: '20px',
      background: 'hotpink',
      maxWidth: '200px',
      margin: '0 auto',
      boxShadow: '0 0 1px 0 rgba(0, 0, 0, 0.5)',
    },
  };
};

const Item = ({ index, classes }): React.ReactElement => (
  <div className={`${classes.root} ${classes.row}`} key={index}>
    <strong>row index {index}</strong>
  </div>
);
const StyledItem = withStyles(styles)(Item);
const myList = new Array(100000).fill(null);

function renderList(visibleNodeCount, startNode) {
  return myList
    .slice(startNode, startNode + visibleNodeCount)
    .map((_, index) => <StyledItem key={index + startNode} index={index + startNode} />);
}

function App() {
  return (
    <div className="App">
      <h1>Virtual Scroll</h1>
      <VirtualScroll
        itemCount={myList.length}
        visibleChildCount={15}
        childHeight={30}
        renderList={renderList}
        childrenUnderFold={5}
      />
    </div>
  );
}

export default App;
