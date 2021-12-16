import { disconnect } from 'process';
import * as vscode from 'vscode';
import {  LanguageClient } from 'vscode-languageclient/node';
import { NbLanguageClient } from './extension';
import { NodeChangedParams, NodeInfoNotification, NodeInfoRequest } from './protocol';

export class TreeViewService extends vscode.Disposable {  
  private handler : vscode.Disposable | undefined;
  private client : NbLanguageClient;
  private trees : Map<string, vscode.TreeView<Visualizer>> = new Map();
  private images : Map<number, vscode.Uri> = new Map();
  private providers : Map<number, VisualizerProvider> = new Map();
  constructor (c : NbLanguageClient, disposeFunc : () => void) {
    super(() => { this.disposeAllViews(); disposeFunc(); });
    this.client = c;
  }

  getClient() : NbLanguageClient {
    return this.client;
  }

  private disposeAllViews() : void {
    for (let tree of this.trees.values()) {
      tree.dispose();
    }
    for (let provider of this.providers.values()) {
      provider.dispose();
    }
    this.trees.clear();
    this.providers.clear();
    this.handler?.dispose();
  }

  public async createView(id : string, title? : string, options? : 
      Partial<vscode.TreeViewOptions<any> & { 
          providerInitializer : (provider : CustomizableTreeDataProvider<Visualizer>) => void }
      >) : Promise<vscode.TreeView<Visualizer>> {
    let tv : vscode.TreeView<Visualizer> | undefined  = this.trees.get(id);
    if (tv) {
      return tv;
    }
    const res = await createViewProvider(this.client, id);
    this.providers.set(res.getRoot().data.id, res);
    options?.providerInitializer?.(res)
    let opts : vscode.TreeViewOptions<Visualizer> = {
      treeDataProvider : res,
      canSelectMany: true,
      showCollapseAll: true,
    }
    
    if (options?.canSelectMany !== undefined) {
      opts.canSelectMany = options.canSelectMany;
    }
    if (options?.showCollapseAll !== undefined) {
      opts.showCollapseAll = options.showCollapseAll;
    }
    let view = vscode.window.createTreeView(id, opts);
    this.trees.set(id, view);
    // this will replace the handler over and over, but never mind
    this.handler = this.client.onNotification(NodeInfoNotification.type, params => this.nodeChanged(params));
    return view;
  }

  private nodeChanged(params : NodeChangedParams) : void {
    let p : VisualizerProvider | undefined = this.providers.get(params.rootId);
    if (p) {
      p.refresh(params);
    }
  }

  imageUri(nodeData : NodeInfoRequest.Data) : vscode.Uri | undefined {
    if (nodeData.iconUri) {
      const uri : vscode.Uri = vscode.Uri.parse(nodeData.iconUri)
      this.images.set(nodeData.iconIndex, uri);
      return uri;
    } else {
      return this.images.get(nodeData.iconIndex);
    }
  }
}

export interface TreeItemDecorator<T> extends vscode.Disposable {
  decorateTreeItem(element: T, item : vscode.TreeItem): vscode.TreeItem | Thenable<vscode.TreeItem>;
}

export interface CustomizableTreeDataProvider<T> extends vscode.TreeDataProvider<T> {
  fireItemChange(item? : T) : void;
  addItemDecorator(deco : TreeItemDecorator<T>) : vscode.Disposable;
}

class VisualizerProvider extends vscode.Disposable implements CustomizableTreeDataProvider<Visualizer> {
  private root: Visualizer;
  private treeData : Map<number, Visualizer> = new Map();
  private decorators : TreeItemDecorator<Visualizer>[] = [];
  private pendingRefresh : Set<number> = new Set();

  constructor(
    private client: LanguageClient,
    private ts : TreeViewService,
    id : string,
    rootData : NodeInfoRequest.Data
  ) {
    super(() => this.disconnect());
    this.root = new Visualizer(rootData, ts.imageUri(rootData));
    this.treeData.set(rootData.id, this.root);
  }

  private _onDidChangeTreeData: vscode.EventEmitter<Visualizer | undefined | null | void> = new vscode.EventEmitter<Visualizer | undefined | null | void>();
  readonly onDidChangeTreeData: vscode.Event<Visualizer | undefined | null | void> = this._onDidChangeTreeData.event;

  private disconnect() : void {
    // nothing at the moment.
    for (let deco of this.decorators) {
      deco.dispose();
    }
  }

  fireItemChange(item : Visualizer) : void {
    if (!item) {
      this._onDidChangeTreeData.fire();
    } else {
      this._onDidChangeTreeData.fire(item);
    }
  }

  addItemDecorator(decoInstance : TreeItemDecorator<Visualizer>) : vscode.Disposable {
    this.decorators.push(decoInstance);
    const self = this;
    return new vscode.Disposable(() => {
      const idx = this.decorators.indexOf(decoInstance);
      if (idx > 0) {
        this.decorators.splice(idx, 1);
        decoInstance.dispose();
      }
    });
  }

  refresh(params : NodeChangedParams): void {
      if (this.root.data.id === params.rootId) {
        if (this.root.data.id == params.nodeId || !params.nodeId) {
          this._onDidChangeTreeData.fire();
        } else {
          this.pendingRefresh.add(params.nodeId);
          let v : Visualizer | undefined = this.treeData.get(params.nodeId);
          if (v) {
              this._onDidChangeTreeData.fire(v);
          }
        }
      }
  }

  getRoot() : Visualizer {
    return this.root.copy();
  }

  returned : Map<number, vscode.TreeItem> = new Map();

  getTreeItem(element: Visualizer): vscode.TreeItem | Thenable<vscode.TreeItem> {
    let c = this.getTreeItem2(element);
    if (c instanceof vscode.TreeItem) {
      let n = Number(c.id);
      const old = this.returned.get(n);
      if (old != null && old !== c) {
        console.log("Error");
      }
      this.returned.set(n, c);
      return c;
    } else {
      return c.then((item) => {
        let n = Number(item.id);
        const old = this.returned.get(n);
        if (old != null && old !== item) {
          console.log("Error");
        }
          this.returned.set(n, item);
        return item;
      });
    }
  }

  getTreeItem2(element: Visualizer): vscode.TreeItem | Thenable<vscode.TreeItem> {
    const n = Number(element.id);
    if (this.pendingRefresh.delete(n)) {
      return this.fetchItem(n).then((newV) => {
        element.update(newV);
        return element;
      });
    }
    if (this.decorators.length == 0) {
      return element;
    }
    let list : TreeItemDecorator<Visualizer>[] = [...this.decorators];
    
    function f(item : vscode.TreeItem) : vscode.TreeItem | Thenable<vscode.TreeItem> {
      const deco = list.shift();
      if (!deco) {
        return item;
      }
      const decorated = deco.decorateTreeItem(element, item);
      if (decorated instanceof vscode.TreeItem) {
          return f(decorated);
      } else {
          return (decorated as Thenable<vscode.TreeItem>).then(f);
      }
    }

    return f(element.copy());
  }

  async fetchItem(n : number) : Promise<Visualizer> {
    let d = await this.client.sendRequest(NodeInfoRequest.info, { nodeId : n });
    if (this.pendingRefresh.delete(n)) {
      // and again
      return this.fetchItem(n);
    }
    let v = new Visualizer(d, this.ts.imageUri(d));
    // console.log('Nodeid ' + d.id + ': visualizer ' + v.visId);
    if (d.command) {
      // PENDING: provide an API to register command (+ parameters) -> command translators.
      if (d.command === 'vscode.open') {
        v.command = { command : d.command, title: '', arguments: [v.resourceUri]};
      } else {
        v.command = { command : d.command, title: '', arguments: [v]};
      }
    }
    return v;
  }

  getChildren(e?: Visualizer): Thenable<Visualizer[]> {
    const self = this;
    async function collectResults(arr: any, element: Visualizer): Promise<Visualizer[]> {
      let res : Visualizer[] = [];
      let refreshAgain : Visualizer[] = [];
      for (let i = 0; i < arr.length; i++) {
        res.push(await self.fetchItem(arr[i]));
      }
      const now : Visualizer[] = element.updateChildren(res, self);
      for (let i = 0; i < arr.length; i++) {
        const v = now[i];
        const n : number = Number(v.id || -1);
        self.treeData.set(n, v);
        v.parent = element;
      }
      return now;
    }

    if (e) {
      return this.client.sendRequest(NodeInfoRequest.children, { nodeId : e.data.id}).then(async (arr) => {
        return collectResults(arr, e);
      });
    } else {
      return this.client.sendRequest(NodeInfoRequest.children, { nodeId: this.root.data.id}).then(async (arr) => {
        return collectResults(arr, this.root);
      });
    }
  }

  removeVisualizers(vis : number[]) {
    let ch : number[] = [];
    vis.forEach(a => {
      let v : Visualizer | undefined = this.treeData.get(a);
      if (v && v.children) {
        ch.push(...v.children.keys());
        this.treeData.delete(a);
      }
    });
    // cascade
    if (ch.length > 0) {
      this.removeVisualizers(ch);
    }
  }
}

// let visualizerSerial = 1;

export class Visualizer extends vscode.TreeItem {

  // visId : number;

  constructor(
    public data : NodeInfoRequest.Data,
    public image : vscode.Uri | undefined
  ) {
    super(data.label, data.collapsibleState);
    this.id = "" + data.id;
    // this.visId = visualizerSerial++;
    this.label = data.label;
    this.description = data.description;
    this.tooltip = data.tooltip;
    this.collapsibleState = data.collapsibleState;
    this.iconPath = image;
    if (data.resourceUri) {
        this.resourceUri = vscode.Uri.parse(data.resourceUri);
    }
    this.contextValue = data.contextValue;
  }

  copy() : Visualizer {
    let v : Visualizer = new Visualizer(this.data, this.image);
    v.id = this.id;
    v.label = this.label;
    v.description = this.description;
    v.tooltip = this.tooltip;
    v.iconPath = this.iconPath;
    v.resourceUri = this.resourceUri;
    v.contextValue = this.contextValue;
    return v;
  }

  parent: Visualizer | null = null;
  children: Map<number, Visualizer> | null = null;

  update(other : Visualizer) {
    // this.visId = visualizerSerial++;
    this.label = other.label;
    this.description = other.description;
    this.tooltip = other.tooltip;
    this.collapsibleState = other.collapsibleState;
    this.iconPath = other.iconPath;
    this.resourceUri = other.resourceUri;
    this.contextValue = other.contextValue;
    this.data = other.data;
    this.image = other.image;
    this.collapsibleState = other.collapsibleState;
    this.command = other.command;
  }

  updateChildren(newChildren : Visualizer[], provider : VisualizerProvider) : Visualizer[] {
    let toRemove : number[] = [];
    let ch : Map<number, Visualizer> = new Map();

    for (let i = 0; i < newChildren.length; i++) {
      let c = newChildren[i];
      const n : number = Number(c.id || -1);
      const v : Visualizer | undefined = this.children?.get(n);
      if (v) {
        v.update(c);
        newChildren[i] = c = v;
      }
      ch.set(n, c);
    }

    if (this.children) {
      for (let k of this.children.keys()) {
        if (!ch.get(k)) {
          toRemove.push(k);
        }
      }
    }
    this.children = ch;
    if (toRemove.length) {
      provider.removeVisualizers(toRemove);
    }
    return newChildren;
  }
}

export async function createViewProvider(c : NbLanguageClient, id : string) : Promise<VisualizerProvider> {
  const ts = c.findTreeViewService();
  const client = ts.getClient();
  const res = client.sendRequest(NodeInfoRequest.explorermanager, { explorerId: id }).then(node => {
    if (!node) {
      throw "Unsupported view: " + id;
    }
    return new VisualizerProvider(client, ts, id, node);
  });
  if (!res) {
    throw "Unsupported view: " + id;
  }
  return res;
}
/**
 * Creates a view of the specified type or returns an existing one. The View has to be registered in package.json in
 * some workspace position. Waits until the view service initializes.
 * 
 * @param id view ID, consistent with package.json registration
 * @param viewTitle title for the new view, optional.
 * @returns promise of the tree view instance.
 */
export async function createTreeView<T>(c: NbLanguageClient, viewId: string, viewTitle? : string, options? : Partial<vscode.TreeViewOptions<any>>) : Promise<vscode.TreeView<Visualizer>> {
  let ts = c.findTreeViewService();
  return ts.createView(viewId, viewTitle, options);
}

/**
 * Registers the treeview service with the language server.
 */
export function createTreeViewService(c : NbLanguageClient): TreeViewService {
    const d = vscode.commands.registerCommand("foundProjects.deleteEntry", async function (this: any, args: any) {
        let v = args as Visualizer;
        let ok = await c.sendRequest(NodeInfoRequest.destroy, { nodeId : v.data.id });
        if (!ok) {
            vscode.window.showErrorMessage('Cannot delete node ' + v.label);
        }
    });
    const ts : TreeViewService = new TreeViewService(c, () => {
      d.dispose()
    });
    return ts;
}

